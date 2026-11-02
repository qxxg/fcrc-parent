package com.fcrc.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;

/**
 * 网关装配测试。
 *
 * <p>验证路由配置能被正确解析（Spring Cloud Gateway 新版本把配置移到了
 * {@code spring.cloud.gateway.server.webflux}），且 7 条业务路由全部加载。
 * 不依赖任何下游服务。</p>
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.cloud.nacos.discovery.enabled=false")
class GatewayApplicationTest {

    @Autowired
    private RouteDefinitionLocator routeDefinitionLocator;

    @Test
    void localProfileLoadsAllRoutes() {
        var routes = routeDefinitionLocator.getRouteDefinitions().collectList().block();
        assertThat(routes).isNotNull();
        assertThat(routes).hasSize(7);
        assertThat(routes).extracting(route -> route.getId())
                .contains("fcrc-customer", "fcrc-risk", "fcrc-compliance", "fcrc-workflow",
                        "fcrc-agent", "fcrc-case", "fcrc-report");
    }
}
