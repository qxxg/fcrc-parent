package com.fcrc.workflow.repository;

import com.fcrc.workflow.domain.WorkflowCheckpointEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Graph 检查点仓储。 */
public interface WorkflowCheckpointRepository extends JpaRepository<WorkflowCheckpointEntity, Long> {

    List<WorkflowCheckpointEntity> findByThreadIdOrderByIdDesc(String threadId);

    Optional<WorkflowCheckpointEntity> findByThreadIdAndCheckpointId(String threadId, String checkpointId);

    Optional<WorkflowCheckpointEntity> findFirstByThreadIdOrderByIdDesc(String threadId);

    void deleteByThreadId(String threadId);
}
