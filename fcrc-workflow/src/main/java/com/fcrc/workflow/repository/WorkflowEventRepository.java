package com.fcrc.workflow.repository;

import com.fcrc.workflow.domain.WorkflowEventEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** 工作流事件仓储。 */
public interface WorkflowEventRepository extends JpaRepository<WorkflowEventEntity, Long> {

    List<WorkflowEventEntity> findByRunNoOrderByIdAsc(String runNo);

    long countByRunNo(String runNo);
}
