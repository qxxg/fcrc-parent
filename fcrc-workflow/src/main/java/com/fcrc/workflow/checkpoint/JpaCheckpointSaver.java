package com.fcrc.workflow.checkpoint;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.checkpoint.BaseCheckpointSaver;
import com.alibaba.cloud.ai.graph.checkpoint.Checkpoint;
import com.alibaba.cloud.ai.graph.serializer.StateSerializer;
import com.fcrc.workflow.domain.WorkflowCheckpointEntity;
import com.fcrc.workflow.repository.WorkflowCheckpointRepository;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 基于数据库的 Graph 检查点存储。
 *
 * <p><b>为什么不能只用 MemorySaver？</b> 人工复核可能持续数小时甚至数天，
 * 期间服务会发布、重启、扩容。内存保存的检查点一旦丢失，流程就无法从暂停点恢复，
 * 只能由人工重新发起，既浪费审核资源，也破坏审计连续性。</p>
 *
 * <p>本实现把 {@link Checkpoint#getState()} 通过 Graph 自带的
 * {@link StateSerializer} 序列化为字节，写入 {@code workflow_checkpoints} 表。
 * 恢复时按 threadId + checkpointId 反序列化，因此跨进程、跨重启可用。</p>
 *
 * <p><b>生产化建议：</b></p>
 * <ul>
 *   <li>为检查点表增加保留策略，流程结束后归档或删除旧记录，避免无限增长；</li>
 *   <li>状态中若含敏感信息，应在序列化前后加密，并限制数据库账号权限；</li>
 *   <li>多实例并发恢复同一 threadId 时，需要数据库行锁或分布式锁，避免双写；</li>
 *   <li>检查点格式变更要做版本迁移，不能直接反序列化旧版本字节。</li>
 * </ul>
 */
public class JpaCheckpointSaver implements BaseCheckpointSaver {

    private static final Logger log = LoggerFactory.getLogger(JpaCheckpointSaver.class);

    private final WorkflowCheckpointRepository repository;
    private final StateSerializer stateSerializer;

    public JpaCheckpointSaver(WorkflowCheckpointRepository repository, StateSerializer stateSerializer) {
        this.repository = repository;
        this.stateSerializer = stateSerializer;
    }

    @Override
    public Collection<Checkpoint> list(RunnableConfig config) {
        String threadId = threadId(config);
        List<Checkpoint> checkpoints = new ArrayList<>();
        for (WorkflowCheckpointEntity entity : repository.findByThreadIdOrderByIdDesc(threadId)) {
            checkpoints.add(toCheckpoint(entity));
        }
        return checkpoints;
    }

    @Override
    public Optional<Checkpoint> get(RunnableConfig config) {
        String threadId = threadId(config);
        if (config.checkPointId().isPresent()) {
            return repository.findByThreadIdAndCheckpointId(threadId, config.checkPointId().get())
                    .map(this::toCheckpoint);
        }
        return repository.findFirstByThreadIdOrderByIdDesc(threadId).map(this::toCheckpoint);
    }

    @Override
    public RunnableConfig put(RunnableConfig config, Checkpoint checkpoint) {
        String threadId = threadId(config);
        byte[] bytes;
        try {
            bytes = stateSerializer.dataToBytes(checkpoint.getState());
        } catch (Exception ex) {
            throw new IllegalStateException("检查点序列化失败: " + checkpoint.getId(), ex);
        }

        if (config.checkPointId().isPresent()) {
            WorkflowCheckpointEntity entity = repository
                    .findByThreadIdAndCheckpointId(threadId, config.checkPointId().get())
                    .orElseThrow(() -> new NoSuchElementException(
                            "检查点不存在: " + config.checkPointId().get()));
            entity.setNodeId(checkpoint.getNodeId());
            entity.setNextNodeId(checkpoint.getNextNodeId());
            entity.setStateData(bytes);
            repository.save(entity);
            return config;
        }

        WorkflowCheckpointEntity entity = new WorkflowCheckpointEntity();
        entity.setCheckpointId(checkpoint.getId());
        entity.setThreadId(threadId);
        entity.setNodeId(checkpoint.getNodeId());
        entity.setNextNodeId(checkpoint.getNextNodeId());
        entity.setStateData(bytes);
        repository.save(entity);
        log.debug("Saved checkpoint {} for thread {} node={} next={}",
                checkpoint.getId(), threadId, checkpoint.getNodeId(), checkpoint.getNextNodeId());
        return RunnableConfig.builder(config)
                .checkPointId(checkpoint.getId())
                .build();
    }

    @Override
    public Tag release(RunnableConfig config) {
        String threadId = threadId(config);
        List<Checkpoint> checkpoints = new ArrayList<>(list(config));
        repository.deleteByThreadId(threadId);
        log.debug("Released {} checkpoints for thread {}", checkpoints.size(), threadId);
        return new Tag(threadId, checkpoints);
    }

    private String threadId(RunnableConfig config) {
        return config.threadId().orElse(THREAD_ID_DEFAULT);
    }

    private Checkpoint toCheckpoint(WorkflowCheckpointEntity entity) {
        try {
            return Checkpoint.builder()
                    .id(entity.getCheckpointId())
                    .state(stateSerializer.dataFromBytes(entity.getStateData()))
                    .nodeId(entity.getNodeId())
                    .nextNodeId(entity.getNextNodeId())
                    .build();
        } catch (Exception ex) {
            throw new IllegalStateException("检查点反序列化失败: " + entity.getCheckpointId(), ex);
        }
    }
}
