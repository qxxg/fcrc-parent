package com.fcrc.cases.controller;

import com.fcrc.cases.dto.AddNoteRequest;
import com.fcrc.cases.dto.AssignCaseRequest;
import com.fcrc.cases.dto.CaseDetail;
import com.fcrc.cases.dto.CaseResponse;
import com.fcrc.cases.dto.CaseStats;
import com.fcrc.cases.dto.CreateCaseRequest;
import com.fcrc.cases.dto.NoteResponse;
import com.fcrc.cases.dto.UpdateCaseStatusRequest;
import com.fcrc.cases.service.CaseService;
import com.fcrc.common.api.ApiResponse;
import com.fcrc.common.api.PageResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 合规案件 REST API。 */
@RestController
@RequestMapping("/api/cases")
public class CaseController {

    private final CaseService service;

    public CaseController(CaseService service) {
        this.service = service;
    }

    @PostMapping
    public ApiResponse<CaseResponse> create(@Valid @RequestBody CreateCaseRequest request) {
        return ApiResponse.ok("案件创建成功", service.create(request));
    }

    @GetMapping
    public ApiResponse<PageResult<CaseResponse>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) String assignee,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(service.search(keyword, status, priority, assignee, page, size));
    }

    @GetMapping("/stats")
    public ApiResponse<CaseStats> stats() {
        return ApiResponse.ok(service.stats());
    }

    @GetMapping("/{caseNo}")
    public ApiResponse<CaseDetail> get(@PathVariable String caseNo) {
        return ApiResponse.ok(service.get(caseNo));
    }

    @PutMapping("/{caseNo}/assign")
    public ApiResponse<CaseResponse> assign(@PathVariable String caseNo,
                                            @Valid @RequestBody AssignCaseRequest request) {
        return ApiResponse.ok("案件已分派", service.assign(caseNo, request));
    }

    @PutMapping("/{caseNo}/status")
    public ApiResponse<CaseResponse> updateStatus(@PathVariable String caseNo,
                                                  @Valid @RequestBody UpdateCaseStatusRequest request) {
        return ApiResponse.ok("案件状态已更新", service.updateStatus(caseNo, request));
    }

    @PostMapping("/{caseNo}/notes")
    public ApiResponse<NoteResponse> addNote(@PathVariable String caseNo,
                                             @Valid @RequestBody AddNoteRequest request) {
        return ApiResponse.ok("备注已记录", service.addNote(caseNo, request));
    }
}
