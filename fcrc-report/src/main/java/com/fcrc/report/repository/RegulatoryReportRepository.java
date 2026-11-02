package com.fcrc.report.repository;

import com.fcrc.report.domain.RegulatoryReportEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** 监管报告仓储。 */
public interface RegulatoryReportRepository extends JpaRepository<RegulatoryReportEntity, Long>,
        JpaSpecificationExecutor<RegulatoryReportEntity> {

    Optional<RegulatoryReportEntity> findByReportNo(String reportNo);

    long countByStatus(String status);

    long countByReportType(String reportType);
}
