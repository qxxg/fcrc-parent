package com.fcrc.compliance.service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * 内置制裁/观察名单与模糊匹配器。
 *
 * <p>真实名单匹配需要处理音译、别名、出生日期、国籍、证件号等多维特征，
 * 并区分“命中”与“误报”。这里用规范化名称 + 编辑距离演示核心思想：
 * <ol>
 *   <li>大小写、空格、标点归一化，降低格式差异导致的漏报；</li>
 *   <li>完全相等或包含关系直接认定为高置信匹配；</li>
 *   <li>其余使用 Levenshtein 相似度，达到阈值即进入人工复核。</li>
 * </ol>
 * 阈值越低召回率越高，但误报也越多，必须由合规团队做规则治理。</p>
 */
@Component
public class SanctionsList {

    private static final double MATCH_THRESHOLD = 0.87;

    private final List<Entry> entries;

    public SanctionsList() {
        this.entries = load();
    }

    public Optional<Match> screen(String name) {
        String candidate = normalize(name);
        if (candidate.isBlank()) {
            return Optional.empty();
        }
        Match best = null;
        for (Entry entry : entries) {
            for (String alias : entry.aliases()) {
                double score = similarity(candidate, normalize(alias));
                if (score >= MATCH_THRESHOLD && (best == null || score > best.score())) {
                    best = new Match(entry, alias, score);
                }
            }
        }
        return Optional.ofNullable(best);
    }

    public int size() {
        return entries.size();
    }

    private List<Entry> load() {
        List<Entry> result = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new ClassPathResource("screening-list.csv").getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                String[] columns = line.split("\\|");
                if (columns.length < 5) {
                    continue;
                }
                List<String> aliases = new ArrayList<>();
                aliases.add(columns[0].trim());
                for (String alias : columns[1].split("\\|")) {
                    if (!alias.isBlank()) {
                        aliases.add(alias.trim());
                    }
                }
                result.add(new Entry(columns[0].trim(), List.copyOf(aliases),
                        columns[2].trim(), columns[3].trim(), columns[4].trim()));
            }
        } catch (Exception ex) {
            throw new IllegalStateException("无法加载制裁名单 screening-list.csv", ex);
        }
        return List.copyOf(result);
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
    }

    private static double similarity(String left, String right) {
        if (left.equals(right)) {
            return 1.0;
        }
        if (left.length() >= 3 && right.length() >= 3
                && (left.contains(right) || right.contains(left))) {
            return 1.0;
        }
        int max = Math.max(left.length(), right.length());
        if (max == 0) {
            return 1.0;
        }
        return 1.0 - (double) levenshtein(left, right) / max;
    }

    private static int levenshtein(String left, String right) {
        int[] previous = new int[right.length() + 1];
        int[] current = new int[right.length() + 1];
        for (int j = 0; j <= right.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= left.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= right.length(); j++) {
                int cost = left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1),
                        previous[j - 1] + cost);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[right.length()];
    }

    /** 名单实体。 */
    public record Entry(String name, List<String> aliases, String type, String program, String country) {
    }

    /** 匹配结果，保留命中别名与相似度，便于人工复核。 */
    public record Match(Entry entry, String matchedAlias, double score) {
    }
}
