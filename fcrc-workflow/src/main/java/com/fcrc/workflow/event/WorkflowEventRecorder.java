package com.fcrc.workflow.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcrc.workflow.domain.WorkflowEventEntity;
import com.fcrc.workflow.repository.WorkflowEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 工作流事件记录器。
 *
 * <p>事件表是“只追加”的审计日志：节点开始/结束、分支决策、人工复核、失败原因
 * 都按发生顺序落库。与运行表不同，事件不保存“最新状态”，而是保存“发生了什么”，
 * 因此可以还原完整时间线，也便于后续接入 Kafka/消息队列做实时风控看板。</p>
 *
 * <p>教学取舍：这里直接写数据库，保证单机演示可运行。生产环境更稳妥的做法是
 * 使用 Outbox 模式——业务事务内写 outbox，再由独立投递器写入事件总线，
 * 避免“业务成功但事件丢失”或“事件成功但业务回滚”。</p>
 */
@Component
public class WorkflowEventRecorder {

    private static final Logger log = LoggerFactory.getLogger(WorkflowEventRecorder.class);

    private final WorkflowEventRepository repository;
    private final ObjectMapper objectMapper;

    public WorkflowEventRecorder(WorkflowEventRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public void record(String runNo, String type, String node, String message, Object payload) {
        if (runNo == null) {
            log.debug("Skip event without runNo: {} {} {}", type, node, message);
            return;
        }
        try {
            WorkflowEventEntity entity = new WorkflowEventEntity();
            entity.setRunNo(runNo);
            entity.setType(type);
            entity.setNode(node);
            entity.setMessage(truncate(message, 500));
            entity.setPayload(payload == null ? null : toJson(payload));
            repository.save(entity);
        } catch (Exception ex) {
            // 事件写入失败不应让正在执行的审批流程崩溃；但必须留下告警，生产环境应接入监控。
            log.error("Failed to record workflow event run={} type={} node={}", runNo, type, node, ex);
        }
    }

    public void nodeStarted(String runNo, String node, String message) {
        record(runNo, "NODE_STARTED", node, message, null);
    }

    public void nodeCompleted(String runNo, String node, String message, Object payload) {
        record(runNo, "NODE_COMPLETED", node, message, payload);
    }

    public void nodeFailed(String runNo, String node, String message, Exception error) {
        record(runNo, "NODE_FAILED", node, message, error == null ? null
                : java.util.Map.of("error", error.getClass().getSimpleName(),
                "detail", String.valueOf(error.getMessage())));
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            return "{\"serializationError\":\"" + ex.getOriginalMessage().replace("\"", "'") + "\"}";
        }
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
