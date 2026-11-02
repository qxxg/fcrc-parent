package com.fcrc.compliance.repository;

import com.fcrc.compliance.domain.ScreeningEntity;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 合规筛查仓储。 */
public interface ScreeningRepository extends JpaRepository<ScreeningEntity, Long> {

    Optional<ScreeningEntity> findByScreeningNo(String screeningNo);

    long countByResult(String result);

    @Query("""
            select s from ScreeningEntity s
            where (:customerNo is null or s.customerNo = :customerNo)
              and (:result is null or s.result = :result)
            """)
    Page<ScreeningEntity> search(@Param("customerNo") String customerNo,
                                 @Param("result") String result,
                                 Pageable pageable);
}
