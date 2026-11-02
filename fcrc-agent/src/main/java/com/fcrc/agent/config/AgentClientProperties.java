package com.fcrc.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 下游微服务地址配置（前缀 {@code fcrc.clients}）。
 *
 * <p>与本项目工作流服务保持同一套约定：本地用 localhost 端口，Docker 用 Compose 服务名，
 * 将来切 Nacos 只需要改这里的 base-url，业务代码不动。</p>
 */
@ConfigurationProperties(prefix = "fcrc.clients")
public class AgentClientProperties {

    private String customer = "http://localhost:8081";
    private String risk = "http://localhost:8082";
    private String compliance = "http://localhost:8083";
    private String workflow = "http://localhost:8084";
    private String cases = "http://localhost:8086";
    private String report = "http://localhost:8087";

    /** 连接超时（毫秒）。 */
    private int connectTimeoutMs = 3000;

    /** 读取超时（毫秒）。AI 调工作流可能触发多次下游调用，适当放宽。 */
    private int readTimeoutMs = 20000;

    public String getCustomer() {
        return customer;
    }

    public void setCustomer(String customer) {
        this.customer = customer;
    }

    public String getRisk() {
        return risk;
    }

    public void setRisk(String risk) {
        this.risk = risk;
    }

    public String getCompliance() {
        return compliance;
    }

    public void setCompliance(String compliance) {
        this.compliance = compliance;
    }

    public String getWorkflow() {
        return workflow;
    }

    public void setWorkflow(String workflow) {
        this.workflow = workflow;
    }

    public String getCases() {
        return cases;
    }

    public void setCases(String cases) {
        this.cases = cases;
    }

    public String getReport() {
        return report;
    }

    public void setReport(String report) {
        this.report = report;
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(int connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    public int getReadTimeoutMs() {
        return readTimeoutMs;
    }

    public void setReadTimeoutMs(int readTimeoutMs) {
        this.readTimeoutMs = readTimeoutMs;
    }
}
