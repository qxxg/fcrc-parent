package com.fcrc.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * FCRC API 网关入口。
 *
 * <p>网关承担三件事：</p>
 * <ol>
 *   <li><b>统一入口：</b> 前端只访问 8080，不关心后端有几个微服务；</li>
 *   <li><b>路由：</b> 本地按端口转发，Docker 环境通过 Nacos + {@code lb://} 负载均衡；</li>
 *   <li><b>静态资源：</b> 直接托管教学演示用的合规驾驶舱页面。</li>
 * </ol>
 *
 * <p>生产环境还应在这里加认证、限流、审计日志与灰度路由；
 * 本项目为了突出 AI 编排，认证保持在"网关注入 userId"的简化模型。</p>
 */
@SpringBootApplication
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
