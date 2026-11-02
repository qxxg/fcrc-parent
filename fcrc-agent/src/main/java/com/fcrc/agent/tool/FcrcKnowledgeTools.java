package com.fcrc.agent.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcrc.agent.config.AgentProperties;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 本地知识库工具。
 *
 * <p>企业合规场景里，模型不能只靠"记忆"回答制度问题。正确做法是把制度文件放在
 * 工作区 {@code knowledge/} 与 {@code skills/} 下，通过工具按需检索。这样：</p>
 * <ul>
 *   <li>答案可追溯到具体制度文件；</li>
 *   <li>制度更新后无需重新训练模型；</li>
 *   <li>检索范围受限于工作区，不会读到服务器上的其他文件。</li>
 * </ul>
 *
 * <p>这里刻意用最简单的关键词打分而不是向量检索：教学项目零外部依赖即可运行，
 * 同时保留了"检索 → 片段 → 引用"的完整链路。生产环境可替换为向量库。</p>
 */
@Component
public class FcrcKnowledgeTools {

    private static final Logger log = LoggerFactory.getLogger(FcrcKnowledgeTools.class);
    private static final int MAX_FILES = 200;
    private static final int MAX_SNIPPET = 600;

    private final AgentProperties properties;
    private final ObjectMapper objectMapper;

    public FcrcKnowledgeTools(AgentProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Tool(name = "search_fcrc_knowledge",
            description = "在合规制度库中检索。query 可以是'高风险国家''PEP''受益所有人''可疑交易'等关键词。"
                    + "返回最相关的文件路径、命中次数和片段，回答制度问题时应优先使用本工具。",
            readOnly = true)
    public String searchKnowledge(
            @ToolParam(name = "query", description = "检索关键词，支持空格分隔多个词") String query,
            @ToolParam(name = "limit", description = "返回条数，默认 5，最大 10", required = false) Integer limit) {
        if (query == null || query.isBlank()) {
            return error("query 不能为空");
        }
        int max = limit == null ? 5 : Math.max(1, Math.min(limit, 10));
        String[] terms = query.toLowerCase(Locale.ROOT).split("\\s+");
        List<Map<String, Object>> hits = new ArrayList<>();
        try {
            for (Path file : candidateFiles()) {
                String content;
                try {
                    content = Files.readString(file, StandardCharsets.UTF_8);
                } catch (IOException ex) {
                    continue;
                }
                String lower = content.toLowerCase(Locale.ROOT);
                int score = 0;
                for (String term : terms) {
                    if (!term.isBlank()) {
                        score += countOccurrences(lower, term);
                    }
                }
                if (score <= 0) {
                    continue;
                }
                Map<String, Object> hit = new LinkedHashMap<>();
                hit.put("path", relative(file));
                hit.put("score", score);
                hit.put("snippet", snippet(content, terms));
                hits.add(hit);
            }
        } catch (IOException ex) {
            log.warn("检索知识库失败", ex);
            return error("检索知识库失败：" + ex.getMessage());
        }
        hits.sort(Comparator.comparingInt(h -> -((Integer) h.get("score"))));
        List<Map<String, Object>> top = hits.stream().limit(max).toList();
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "query", query,
                    "hits", top,
                    "totalHits", hits.size()));
        } catch (Exception ex) {
            return error("结果序列化失败");
        }
    }

    @Tool(name = "read_policy_document",
            description = "读取合规制度库中的指定文件。path 必须来自 search_fcrc_knowledge 返回的相对路径，"
                    + "只允许 knowledge/ 与 skills/ 目录。",
            readOnly = true)
    public String readDocument(
            @ToolParam(name = "path", description = "相对路径，例如 knowledge/KNOWLEDGE.md") String path) {
        if (path == null || path.isBlank()) {
            return error("path 不能为空");
        }
        Path workspace = properties.getWorkspace().toAbsolutePath().normalize();
        Path target = workspace.resolve(path).normalize();
        // 防路径穿越：只允许 knowledge/ 与 skills/ 下的文件
        boolean allowed = target.startsWith(workspace.resolve("knowledge"))
                || target.startsWith(workspace.resolve("skills"));
        if (!allowed || !Files.isRegularFile(target)) {
            return error("文件不存在或不允许访问：" + path);
        }
        try {
            String content = Files.readString(target, StandardCharsets.UTF_8);
            return objectMapper.writeValueAsString(Map.of(
                    "path", path,
                    "content", content));
        } catch (Exception ex) {
            return error("读取失败：" + ex.getMessage());
        }
    }

    private List<Path> candidateFiles() throws IOException {
        List<Path> files = new ArrayList<>();
        for (String dir : List.of("knowledge", "skills")) {
            Path root = properties.getWorkspace().resolve(dir);
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (Stream<Path> stream = Files.walk(root)) {
                stream.filter(Files::isRegularFile)
                        .filter(p -> {
                            String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
                            return name.endsWith(".md") || name.endsWith(".txt");
                        })
                        .limit(MAX_FILES)
                        .forEach(files::add);
            }
        }
        return files;
    }

    private String relative(Path file) {
        return properties.getWorkspace().toAbsolutePath().normalize()
                .relativize(file.toAbsolutePath().normalize())
                .toString()
                .replace('\\', '/');
    }

    private int countOccurrences(String text, String term) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(term, index)) >= 0) {
            count++;
            index += term.length();
            if (count >= 50) {
                break;
            }
        }
        return count;
    }

    private String snippet(String content, String[] terms) {
        String lower = content.toLowerCase(Locale.ROOT);
        int position = -1;
        for (String term : terms) {
            if (term.isBlank()) {
                continue;
            }
            int index = lower.indexOf(term);
            if (index >= 0 && (position < 0 || index < position)) {
                position = index;
            }
        }
        if (position < 0) {
            position = 0;
        }
        int start = Math.max(0, position - 160);
        int end = Math.min(content.length(), start + MAX_SNIPPET);
        String text = content.substring(start, end).replaceAll("\\s+", " ").trim();
        return (start > 0 ? "..." : "") + text + (end < content.length() ? "..." : "");
    }

    private String error(String message) {
        try {
            return objectMapper.writeValueAsString(Map.of("error", message));
        } catch (Exception ex) {
            return "{\"error\":\"知识库工具失败\"}";
        }
    }
}
