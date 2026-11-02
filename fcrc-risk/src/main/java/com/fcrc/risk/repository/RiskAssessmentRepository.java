package com.fcrc.risk.repository;

import com.fcrc.risk.domain.RiskAssessmentEntity;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 风险评估仓储。 */
public interface RiskAssessmentRepository extends JpaRepository<RiskAssessmentEntity, Long> {

    Optional<RiskAssessmentEntity> findByAssessmentNo(String assessmentNo);

    long countByLevel(String level);

    @Query("select avg(r.score) from RiskAssessmentEntity r")
    Double averageScore();

    @Query("""
            select r from RiskAssessmentEntity r
            where (:customerNo is null or r.customerNo = :customerNo)
              and (:level is null or r.level = :level)
            """)
    Page<RiskAssessmentEntity> search(@Param("customerNo") String customerNo,
                                      @Param("level") String level,
                                      Pageable pageable);
}
