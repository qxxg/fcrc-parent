package com.fcrc.report.controller;

import com.fcrc.common.api.ApiResponse;
import com.fcrc.common.api.PageResult;
import com.fcrc.report.dto.GenerateReportRequest;
import com.fcrc.report.dto.ReportResponse;
import com.fcrc.report.dto.ReportStats;
import com.fcrc.report.service.ReportService;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 监管报告 REST API。 */
@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService service;

    public ReportController(ReportService service) {
        this.service = service;
    }

    @PostMapping
    public ApiResponse<ReportResponse> generate(@Valid @RequestBody GenerateReportRequest request) {
        return ApiResponse.ok("报告草稿已生成", service.generate(request));
    }

    @GetMapping
    public ApiResponse<PageResult<ReportResponse>> list(
            @RequestParam(required = false) String customerNo,
            @RequestParam(required = false) String reportType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String period,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(service.search(customerNo, reportType, status, period, page, size));
    }

    @GetMapping("/stats")
    public ApiResponse<ReportStats> stats() {
        return ApiResponse.ok(service.stats());
    }

    @GetMapping("/{reportNo}")
    public ApiResponse<ReportResponse> get(@PathVariable String reportNo) {
        return ApiResponse.ok(service.get(reportNo));
    }

    @PostMapping("/{reportNo}/finalize")
    public ApiResponse<ReportResponse> finalizeReport(@PathVariable String reportNo) {
        return ApiResponse.ok("报告已定稿", service.finalizeReport(reportNo));
    }

    @PostMapping("/{reportNo}/submit")
    public ApiResponse<ReportResponse> submit(@PathVariable String reportNo) {
        return ApiResponse.ok("报告已标记为已报送", service.submit(reportNo));
    }

    /** 导出单个报告为 UTF-8 BOM CSV，Excel 可直接打开中文。 */
    @GetMapping("/{reportNo}/csv")
    public ResponseEntity<byte[]> downloadCsv(@PathVariable String reportNo) {
        byte[] content = service.exportCsv(reportNo);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("text", "csv", StandardCharsets.UTF_8));
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(reportNo + ".csv", StandardCharsets.UTF_8).build());
        return ResponseEntity.ok().headers(headers).body(content);
    }
}
