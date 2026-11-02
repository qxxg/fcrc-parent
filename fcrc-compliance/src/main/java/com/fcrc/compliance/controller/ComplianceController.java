package com.fcrc.compliance.controller;

import com.fcrc.common.api.ApiResponse;
import com.fcrc.common.api.PageResult;
import com.fcrc.compliance.dto.ComplianceScreenRequest;
import com.fcrc.compliance.dto.ComplianceStats;
import com.fcrc.compliance.dto.ScreeningResponse;
import com.fcrc.compliance.service.ComplianceService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 合规筛查 REST API。 */
@RestController
@RequestMapping("/api/compliance")
public class ComplianceController {

    private final ComplianceService service;

    public ComplianceController(ComplianceService service) {
        this.service = service;
    }

    @PostMapping("/screen")
    public ApiResponse<ScreeningResponse> screen(@Valid @RequestBody ComplianceScreenRequest request) {
        return ApiResponse.ok("合规筛查完成", service.screen(request));
    }

    @GetMapping("/screenings")
    public ApiResponse<PageResult<ScreeningResponse>> list(
            @RequestParam(required = false) String customerNo,
            @RequestParam(required = false) String result,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(service.search(customerNo, result, page, size));
    }

    @GetMapping("/screenings/{screeningNo}")
    public ApiResponse<ScreeningResponse> get(@PathVariable String screeningNo) {
        return ApiResponse.ok(service.get(screeningNo));
    }

    @GetMapping("/stats")
    public ApiResponse<ComplianceStats> stats() {
        return ApiResponse.ok(service.stats());
    }
}
