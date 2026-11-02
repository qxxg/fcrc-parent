package com.fcrc.cases.repository;

import com.fcrc.cases.domain.ComplianceCaseEntity;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 案件仓储，支持组合条件分页。 */
public interface CaseRepository extends JpaRepository<ComplianceCaseEntity, Long>,
        JpaSpecificationExecutor<ComplianceCaseEntity> {

    Optional<ComplianceCaseEntity> findByCaseNo(String caseNo);

    long countByStatus(String status);

    long countByPriority(String priority);

    @Query("""
            select count(c) from ComplianceCaseEntity c
            where c.dueAt < :now and c.status not in ('RESOLVED', 'CLOSED')
            """)
    long countOverdue(@Param("now") Instant now);
}
