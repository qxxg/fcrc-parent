package com.fcrc.cases;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 合规案件管理微服务。
 *
 * <p>风险与合规引擎输出“信号”，案件系统负责把信号变成有责任人、有时限、有审计轨迹的处置任务。
 * 状态机、乐观锁和备注留痕是监管检查最关注的三项能力。</p>
 */
@SpringBootApplication(scanBasePackages = "com.fcrc")
public class CaseApplication {

    public static void main(String[] args) {
        SpringApplication.run(CaseApplication.class, args);
    }
}
