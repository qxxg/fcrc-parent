package com.fcrc.cases.service;

import com.fcrc.cases.domain.CaseNoteEntity;
import com.fcrc.cases.domain.ComplianceCaseEntity;
import com.fcrc.cases.dto.AddNoteRequest;
import com.fcrc.cases.dto.AssignCaseRequest;
import com.fcrc.cases.dto.CaseDetail;
import com.fcrc.cases.dto.CaseResponse;
import com.fcrc.cases.dto.CaseStats;
import com.fcrc.cases.dto.CreateCaseRequest;
import com.fcrc.cases.dto.NoteResponse;
import com.fcrc.cases.dto.UpdateCaseStatusRequest;
import com.fcrc.cases.repository.CaseNoteRepository;
import com.fcrc.cases.repository.CaseRepository;
import com.fcrc.common.api.PageResult;
import com.fcrc.common.exception.BusinessException;
import com.fcrc.common.exception.ErrorCode;
import com.fcrc.common.util.BusinessIds;
import jakarta.persistence.criteria.Predicate;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 案件应用服务：创建、分派、状态机流转与审计备注。 */
@Service
public class CaseService {

    /** 每个状态允许迁往的下一状态。终态 CLOSED 不再允许流转。 */
    private static final Map<String, Set<String>> TRANSITIONS = Map.of(
            "OPEN", Set.of("ASSIGNED", "IN_PROGRESS"),
            "ASSIGNED", Set.of("IN_PROGRESS", "PENDING_INFO", "RESOLVED"),
            "IN_PROGRESS", Set.of("PENDING_INFO", "RESOLVED"),
            "PENDING_INFO", Set.of("IN_PROGRESS", "RESOLVED"),
            "RESOLVED", Set.of("IN_PROGRESS", "CLOSED"),
            "CLOSED", Set.of());

    private final CaseRepository caseRepository;
    private final CaseNoteRepository noteRepository;

    public CaseService(CaseRepository caseRepository, CaseNoteRepository noteRepository) {
        this.caseRepository = caseRepository;
        this.noteRepository = noteRepository;
    }

    @Transactional
    public CaseResponse create(CreateCaseRequest request) {
        ComplianceCaseEntity entity = new ComplianceCaseEntity();
        entity.setCaseNo(BusinessIds.next("CASE"));
        entity.setCustomerNo(request.customerNo());
        entity.setCustomerName(request.customerName());
        entity.setType(request.type());
        entity.setPriority(request.priority());
        entity.setTitle(request.title());
        entity.setDescription(request.description());
        entity.setSourceRef(request.sourceRef());
        entity.setStatus("OPEN");
        entity.setDueAt(Instant.now().plus(Duration.ofDays(slaDays(request.priority()))));
        caseRepository.save(entity);
        addNote(entity.getCaseNo(), "SYSTEM", "案件创建，SLA 截止时间 " + entity.getDueAt());
        return CaseResponse.from(entity);
    }

    @Transactional(readOnly = true)
    public PageResult<CaseResponse> search(String keyword, String status, String priority, String assignee,
                                           int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        int safePage = Math.max(page, 0);
        Specification<ComplianceCaseEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (notBlank(keyword)) {
                String like = "%" + keyword.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("caseNo")), like),
                        cb.like(cb.lower(root.get("customerNo")), like),
                        cb.like(cb.lower(root.get("title")), like)));
            }
            if (notBlank(status)) {
                predicates.add(cb.equal(root.get("status"), status.trim()));
            }
            if (notBlank(priority)) {
                predicates.add(cb.equal(root.get("priority"), priority.trim()));
            }
            if (notBlank(assignee)) {
                predicates.add(cb.equal(root.get("assignee"), assignee.trim()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Page<ComplianceCaseEntity> result = caseRepository.findAll(spec,
                PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt")));
        return PageResult.of(result.getContent().stream().map(CaseResponse::from).toList(),
                result.getTotalElements(), safePage, safeSize);
    }

    @Transactional(readOnly = true)
    public CaseDetail get(String caseNo) {
        ComplianceCaseEntity entity = require(caseNo);
        List<NoteResponse> notes = noteRepository.findByCaseNoOrderByCreatedAtAsc(caseNo).stream()
                .map(NoteResponse::from).toList();
        return new CaseDetail(CaseResponse.from(entity), notes);
    }

    @Transactional
    public CaseResponse assign(String caseNo, AssignCaseRequest request) {
        ComplianceCaseEntity entity = require(caseNo);
        if ("CLOSED".equals(entity.getStatus())) {
            throw new BusinessException(ErrorCode.ILLEGAL_STATE, "已关闭案件不能重新分派");
        }
        entity.setAssignee(request.assignee());
        if ("OPEN".equals(entity.getStatus())) {
            entity.setStatus("ASSIGNED");
        }
        addNote(caseNo, "SYSTEM", "案件分派给 " + request.assignee());
        return CaseResponse.from(entity);
    }

    @Transactional
    public CaseResponse updateStatus(String caseNo, UpdateCaseStatusRequest request) {
        ComplianceCaseEntity entity = require(caseNo);
        String current = entity.getStatus();
        String target = request.status();
        if (!current.equals(target)
                && !TRANSITIONS.getOrDefault(current, Set.of()).contains(target)) {
            throw new BusinessException(ErrorCode.ILLEGAL_STATE,
                    "案件状态不允许从 " + current + " 变更为 " + target);
        }
        if (("RESOLVED".equals(target) || "CLOSED".equals(target))
                && (request.resolution() == null || request.resolution().isBlank())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "解决或关闭案件必须填写处置结论");
        }
        if (request.resolution() != null) {
            entity.setResolution(request.resolution());
        }
        entity.setStatus(target);
        addNote(caseNo, "SYSTEM", "状态由 " + current + " 变更为 " + target
                + (request.resolution() == null ? "" : "；结论：" + request.resolution()));
        return CaseResponse.from(entity);
    }

    @Transactional
    public NoteResponse addNote(String caseNo, AddNoteRequest request) {
        require(caseNo);
        return addNote(caseNo, request.author(), request.content());
    }

    private NoteResponse addNote(String caseNo, String author, String content) {
        CaseNoteEntity note = new CaseNoteEntity();
        note.setCaseNo(caseNo);
        note.setAuthor(author);
        note.setContent(content);
        noteRepository.save(note);
        return NoteResponse.from(note);
    }

    @Transactional(readOnly = true)
    public CaseStats stats() {
        return new CaseStats(
                caseRepository.count(),
                caseRepository.countByStatus("OPEN"),
                caseRepository.countByStatus("IN_PROGRESS") + caseRepository.countByStatus("ASSIGNED"),
                caseRepository.countByStatus("PENDING_INFO"),
                caseRepository.countByStatus("RESOLVED"),
                caseRepository.countByStatus("CLOSED"),
                caseRepository.countOverdue(Instant.now()),
                caseRepository.countByPriority("CRITICAL"));
    }

    private ComplianceCaseEntity require(String caseNo) {
        return caseRepository.findByCaseNo(caseNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "案件不存在: " + caseNo));
    }

    private static int slaDays(String priority) {
        return switch (priority) {
            case "CRITICAL" -> 1;
            case "HIGH" -> 3;
            case "MEDIUM" -> 7;
            default -> 14;
        };
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
