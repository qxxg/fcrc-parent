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
 * 工作流事件：只追加、不修改，是审计与实时看板的数据源。
 *
 * <p>事件表使用数据库自增主键作为顺序号，避免应用层“取最大序号 + 1”的竞态。
 * 多实例部署时，如果要获得严格的全序，应使用数据库序列或消息队列分区键；
 * 本项目按 runNo 维度阅读，自增 ID 已足够稳定。</p>
 */
@Entity
@Table(name = "workflow_events", indexes = {
        @Index(name = "idx_wf_event_run", columnList = "runNo")
})
public class WorkflowEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40)
    private String runNo;

    /** WORKFLOW_STARTED / NODE_STARTED / NODE_COMPLETED / BRANCH_DECISION / HUMAN_REVIEW... */
    @Column(nullable = false, length = 40)
    private String type;

    @Column(length = 64)
    private String node;

    @Column(nullable = false, length = 500)
    private String message;

    /** 结构化补充信息，JSON 字符串；长度可控，避免把大对象塞进事件表。 */
    @Lob
    private String payload;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getRunNo() {
        return runNo;
    }

    public void setRunNo(String runNo) {
        this.runNo = runNo;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getNode() {
        return node;
    }

    public void setNode(String node) {
        this.node = node;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
