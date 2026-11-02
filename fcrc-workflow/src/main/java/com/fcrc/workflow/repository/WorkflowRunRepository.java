package com.fcrc.workflow.repository;

import com.fcrc.workflow.domain.WorkflowRunEntity;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** 工作流运行仓储。 */
public interface WorkflowRunRepository extends JpaRepository<WorkflowRunEntity, Long>,
        JpaSpecificationExecutor<WorkflowRunEntity> {

    Optional<WorkflowRunEntity> findByRunNo(String runNo);

    long countByStatus(String status);

    Page<WorkflowRunEntity> findByCustomerNoOrderByCreatedAtDesc(String customerNo, Pageable pageable);
}
