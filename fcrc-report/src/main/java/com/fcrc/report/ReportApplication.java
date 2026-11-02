package com.fcrc.report;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 监管报送微服务。
 *
 * <p>把客户开户工作流的风险、合规与决策结果固化为可追溯报告，支持草稿/定稿/已报送状态。
 * 演示项目生成 JSON 与 CSV；生产环境通常需要 XBRL、PDF/A 或监管机构指定报文格式，
 * 并配合数字签名与报送回执管理。</p>
 */
@SpringBootApplication(scanBasePackages = "com.fcrc")
public class ReportApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReportApplication.class, args);
    }
}
