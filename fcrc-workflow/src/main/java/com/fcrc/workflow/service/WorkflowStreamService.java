package com.fcrc.workflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcrc.common.exception.BusinessException;
import com.fcrc.common.exception.ErrorCode;
import com.fcrc.workflow.domain.WorkflowEventEntity;
import com.fcrc.workflow.domain.WorkflowRunEntity;
import com.fcrc.workflow.repository.WorkflowEventRepository;
import com.fcrc.workflow.repository.WorkflowRunRepository;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 工作流事件 SSE 推送。
 *
 * <p>为什么用“轮询事件表 + SSE”，而不是直接在节点里操作 emitter？</p>
 * <ol>
 *   <li>节点可能运行在并行线程，甚至未来迁移到独立 worker，不能依赖 Web 请求线程；</li>
 *   <li>事件已经持久化，新打开的页面可以补齐历史，不会漏掉连接前的节点；</li>
 *   <li>SSE 只是传输层，业务事件仍由数据库/消息队列驱动，架构更稳。</li>
 * </ol>
 *
 * <p>生产环境可把轮询替换为 Kafka/Redis PubSub，接口保持不变。</p>
 */
@Service
public class WorkflowStreamService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowStreamService.class);
    private static final Set<String> TERMINAL = Set.of("COMPLETED", "REJECTED", "FAILED");

    private final WorkflowEventRepository eventRepository;
    private final WorkflowRunRepository runRepository;
    private final ObjectMapper objectMapper;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4, runnable -> {
        Thread thread = new Thread(runnable, "workflow-sse");
        thread.setDaemon(true);
        return thread;
    });

    public WorkflowStreamService(WorkflowEventRepository eventRepository,
                                 WorkflowRunRepository runRepository,
                                 ObjectMapper objectMapper) {
        this.eventRepository = eventRepository;
        this.runRepository = runRepository;
        this.objectMapper = objectMapper;
    }

    public SseEmitter stream(String runNo) {
        WorkflowRunEntity run = runRepository.findByRunNo(runNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "工作流不存在: " + runNo));

        SseEmitter emitter = new SseEmitter(Duration.ofMinutes(30).toMillis());
        AtomicLong lastId = new AtomicLong(0);
        AtomicBoolean stopped = new AtomicBoolean(false);
        AtomicReference<ScheduledFuture<?>> futureRef = new AtomicReference<>();

        Runnable poller = () -> {
            if (stopped.get()) {
                return;
            }
            try {
                for (WorkflowEventEntity event : eventRepository.findByRunNoOrderByIdAsc(runNo)) {
                    if (event.getId() > lastId.get()) {
                        emitter.send(SseEmitter.event()
                                .id(String.valueOf(event.getId()))
                                .name("workflow-event")
                                .data(toJson(event)));
                        lastId.set(event.getId());
                    }
                }

                WorkflowRunEntity latest = runRepository.findByRunNo(runNo).orElse(null);
                if (latest != null && TERMINAL.contains(latest.getStatus())) {
                    emitter.send(SseEmitter.event().name("done")
                            .data(toJson(Map.of("runNo", runNo, "status", latest.getStatus()))));
                    stopped.set(true);
                    emitter.complete();
                    cancel(futureRef);
                }
            } catch (IllegalStateException closed) {
                stopped.set(true);
                cancel(futureRef);
            } catch (IOException | RuntimeException ex) {
                log.debug("SSE stream closed for run {}: {}", runNo, ex.getMessage());
                stopped.set(true);
                cancel(futureRef);
                emitter.completeWithError(ex);
            }
        };

        futureRef.set(scheduler.scheduleWithFixedDelay(poller, 200, 700, TimeUnit.MILLISECONDS));
        emitter.onCompletion(() -> {
            stopped.set(true);
            cancel(futureRef);
        });
        emitter.onTimeout(() -> {
            stopped.set(true);
            cancel(futureRef);
            emitter.complete();
        });
        emitter.onError(error -> {
            stopped.set(true);
            cancel(futureRef);
        });

        // 先推一条快照，前端可立即渲染当前状态，而不必等下一次轮询。
        try {
            emitter.send(SseEmitter.event().name("snapshot")
                    .data(toJson(Map.of("runNo", run.getRunNo(), "status", run.getStatus()))));
        } catch (IOException ex) {
            emitter.completeWithError(ex);
        }
        return emitter;
    }

    private void cancel(AtomicReference<ScheduledFuture<?>> futureRef) {
        ScheduledFuture<?> future = futureRef.get();
        if (future != null) {
            future.cancel(false);
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return "{\"error\":\"serialization\"}";
        }
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdownNow();
    }
}
