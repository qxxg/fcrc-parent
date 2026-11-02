package com.fcrc.workflow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Graph 检查点持久化实体。
 *
 * <p>Spring AI Alibaba Graph 自带的 {@code MemorySaver} 只适合单进程演示；
 * 一旦服务重启，人工复核中的流程就无法恢复。企业实现必须把检查点外置。
 * 这里使用数据库保存序列化后的状态字节，配合 {@code JpaCheckpointSaver}
 * 实现“重启后可继续审批”。生产环境还可替换为 Redis / PostgreSQL / 对象存储，
 * 并增加版本号、加密与保留策略。</p>
 */
@Entity
@Table(name = "workflow_checkpoints", indexes = {
        @Index(name = "idx_wf_checkpoint_thread", columnList = "threadId")
})
public class WorkflowCheckpointEntity {

    /** 自增主键仅用于稳定排序；真正的检查点 ID 是 checkpointId。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String checkpointId;

    @Column(nullable = false, length = 64)
    private String threadId;

    @Column(nullable = false, length = 64)
    private String nodeId;

    @Column(nullable = false, length = 64)
    private String nextNodeId;

    /** 由 StateSerializer 序列化的图状态，保留 Java 类型信息。 */
    @Lob
    @Column(nullable = false)
    private byte[] stateData;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getCheckpointId() {
        return checkpointId;
    }

    public void setCheckpointId(String checkpointId) {
        this.checkpointId = checkpointId;
    }

    public String getThreadId() {
        return threadId;
    }

    public void setThreadId(String threadId) {
        this.threadId = threadId;
    }

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public String getNextNodeId() {
        return nextNodeId;
    }

    public void setNextNodeId(String nextNodeId) {
        this.nextNodeId = nextNodeId;
    }

    public byte[] getStateData() {
        return stateData;
    }

    public void setStateData(byte[] stateData) {
        this.stateData = stateData;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
