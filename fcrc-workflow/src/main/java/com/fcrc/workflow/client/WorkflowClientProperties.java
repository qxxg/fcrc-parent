package com.fcrc.workflow.client;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 下游服务地址配置。
 *
 * <p>教学项目把地址显式配置化，原因有三：</p>
 * <ol>
 *   <li>本地开发时服务分别运行在 localhost 的不同端口，无需启动 Nacos；</li>
 *   <li>Docker 部署时直接使用 Compose 服务名（如 http://fcrc-risk:8082），
 *       容器 DNS 比服务发现更简单可靠；</li>
 *   <li>生产环境可替换为 Spring Cloud LoadBalancer + Nacos 服务名，
 *       只需修改 base-url，不必改动任何业务代码。</li>
 * </ol>
 *
 * <p>网关层使用 Nacos 的 {@code lb://fcrc-*} 路由，前端请求仍然走服务发现；
 * 服务间的同步调用则通过配置注入，避免“为了演示发现而把简单调用复杂化”。</p>
 */
@ConfigurationProperties(prefix = "fcrc.clients")
public class WorkflowClientProperties {

    private final Downstream customer = new Downstream("http://localhost:8081");
    private final Downstream risk = new Downstream("http://localhost:8082");
    private final Downstream compliance = new Downstream("http://localhost:8083");
    private final Downstream cases = new Downstream("http://localhost:8086");
    private final Downstream report = new Downstream("http://localhost:8087");

    private Duration connectTimeout = Duration.ofSeconds(2);
    private Duration readTimeout = Duration.ofSeconds(10);

    public Downstream getCustomer() {
        return customer;
    }

    public Downstream getRisk() {
        return risk;
    }

    public Downstream getCompliance() {
        return compliance;
    }

    public Downstream getCases() {
        return cases;
    }

    public Downstream getReport() {
        return report;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
    }

    public static class Downstream {

        private String baseUrl;

        public Downstream() {
        }

        public Downstream(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }
    }
}
