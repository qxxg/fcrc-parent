package com.fcrc.compliance;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 合规筛查微服务。
 *
 * <p>覆盖制裁名单、PEP、负面媒体、KYC 完整性与跨境业务背景检查。
 * 演示版使用内置名单，生产环境应对接 Dow Jones/Refinitiv/World-Check 等供应商，
 * 并保留名单版本号、匹配算法版本与人工处置记录。</p>
 */
@SpringBootApplication(scanBasePackages = "com.fcrc")
public class ComplianceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ComplianceApplication.class, args);
    }
}
