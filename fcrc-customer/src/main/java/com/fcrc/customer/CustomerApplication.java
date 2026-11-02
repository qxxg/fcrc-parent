package com.fcrc.customer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 客户中心微服务启动类。
 *
 * <p>客户中心是整个 KYC（Know Your Customer）链路的起点：
 * 负责客户建档、资料脱敏展示、KYC 状态流转，并通过内部接口向编排服务提供客户画像。
 * 服务注册到 Nacos，网关按服务名转发，因此客户端不感知实例 IP。</p>
 */
@SpringBootApplication(scanBasePackages = "com.fcrc")
public class CustomerApplication {

    public static void main(String[] args) {
        SpringApplication.run(CustomerApplication.class, args);
    }
}
