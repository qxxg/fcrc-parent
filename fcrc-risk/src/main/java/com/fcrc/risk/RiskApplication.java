package com.fcrc.risk;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 风险评估微服务。
 *
 * <p>职责：把客户画像、交易特征映射为可解释的风险分与风险等级。
 * 规则引擎刻意保持确定性（可审计、可回归测试），AI 只用于生成解释，不参与最终定级。</p>
 */
@SpringBootApplication(scanBasePackages = "com.fcrc")
public class RiskApplication {

    public static void main(String[] args) {
        SpringApplication.run(RiskApplication.class, args);
    }
}
