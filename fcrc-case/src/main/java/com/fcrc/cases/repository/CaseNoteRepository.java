package com.fcrc.cases.repository;

import com.fcrc.cases.domain.CaseNoteEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** 案件备注仓储。 */
public interface CaseNoteRepository extends JpaRepository<CaseNoteEntity, Long> {

    List<CaseNoteEntity> findByCaseNoOrderByCreatedAtAsc(String caseNo);
}
