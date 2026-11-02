package com.fcrc.risk.controller;

import com.fcrc.common.api.ApiResponse;
import com.fcrc.common.api.PageResult;
import com.fcrc.risk.dto.RiskAssessmentRequest;
import com.fcrc.risk.dto.RiskAssessmentResponse;
import com.fcrc.risk.dto.RiskStats;
import com.fcrc.risk.service.RiskService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 风险评估 REST API。 */
@RestController
@RequestMapping("/api/risk")
public class RiskController {

    private final RiskService service;

    public RiskController(RiskService service) {
        this.service = service;
    }

    @PostMapping("/assess")
    public ApiResponse<RiskAssessmentResponse> assess(@Valid @RequestBody RiskAssessmentRequest request) {
        return ApiResponse.ok("风险评估完成", service.assess(request));
    }

    @GetMapping("/assessments")
    public ApiResponse<PageResult<RiskAssessmentResponse>> list(
            @RequestParam(required = false) String customerNo,
            @RequestParam(required = false) String level,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(service.search(customerNo, level, page, size));
    }

    @GetMapping("/assessments/{assessmentNo}")
    public ApiResponse<RiskAssessmentResponse> get(@PathVariable String assessmentNo) {
        return ApiResponse.ok(service.get(assessmentNo));
    }

    @GetMapping("/stats")
    public ApiResponse<RiskStats> stats() {
        return ApiResponse.ok(service.stats());
    }
}
