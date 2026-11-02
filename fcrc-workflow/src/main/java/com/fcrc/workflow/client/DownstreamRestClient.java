package com.fcrc.workflow.client;

import com.fcrc.common.api.ApiResponse;
import com.fcrc.common.exception.BusinessException;
import com.fcrc.common.exception.ErrorCode;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * 轻量级下游调用器。
 *
 * <p>企业项目里最常见的错误不是“不会调用 HTTP”，而是把 HTTP 细节散落在业务代码中：
 * 超时配置不一致、错误信息被吞掉、每个服务各写一套异常转换。这里把 GET/POST/PUT
 * 以及 {@link ApiResponse} 解包集中在一个类里，业务客户端只表达“调用什么”。</p>
 *
 * <p>关于重试：不是所有调用都可以重试。查询和幂等的状态回写可以重试；
 * 创建案件、生成报告等 POST 操作如果下游没有幂等键，重试可能产生重复数据。
 * 因此本类不自动重试，由调用方按业务语义决定。工作流的每个节点失败后会把
 * 运行标记为 FAILED，人工可从事件时间线定位失败点。</p>
 */
@Component
public class DownstreamRestClient {

    private static final Logger log = LoggerFactory.getLogger(DownstreamRestClient.class);

    private final WorkflowClientProperties properties;
    private final Map<String, RestClient> clients = new ConcurrentHashMap<>();

    public DownstreamRestClient(WorkflowClientProperties properties) {
        this.properties = properties;
    }

    public <T> T get(WorkflowClientProperties.Downstream downstream, String uri,
                     ParameterizedTypeReference<ApiResponse<T>> type) {
        return exchange(downstream.getBaseUrl(), uri,
                client -> client.get().uri(uri).retrieve().body(type));
    }

    public <T> T post(WorkflowClientProperties.Downstream downstream, String uri, Object body,
                      ParameterizedTypeReference<ApiResponse<T>> type) {
        return exchange(downstream.getBaseUrl(), uri,
                client -> client.post().uri(uri).body(body).retrieve().body(type));
    }

    public <T> T put(WorkflowClientProperties.Downstream downstream, String uri, Object body,
                     ParameterizedTypeReference<ApiResponse<T>> type) {
        return exchange(downstream.getBaseUrl(), uri,
                client -> client.put().uri(uri).body(body).retrieve().body(type));
    }

    private <T> T exchange(String baseUrl, String uri, RestClientCall<ApiResponse<T>> call) {
        try {
            ApiResponse<T> response = call.execute(client(baseUrl));
            if (response == null) {
                throw new BusinessException(ErrorCode.UPSTREAM_ERROR, "下游服务返回空响应: " + uri);
            }
            if (!response.success()) {
                throw new BusinessException(ErrorCode.UPSTREAM_ERROR,
                        "下游服务返回失败: " + response.code() + " " + response.message());
            }
            return response.data();
        } catch (BusinessException ex) {
            throw ex;
        } catch (RestClientResponseException ex) {
            log.warn("Downstream call failed: {} {} -> {}", baseUrl, uri, ex.getStatusCode());
            throw new BusinessException(ErrorCode.UPSTREAM_ERROR,
                    "下游服务调用失败: " + ex.getStatusCode() + " " + uri);
        } catch (Exception ex) {
            log.warn("Downstream call error: {} {}", baseUrl, uri, ex);
            throw new BusinessException(ErrorCode.UPSTREAM_ERROR, "下游服务不可用: " + uri);
        }
    }

    private RestClient client(String baseUrl) {
        return clients.computeIfAbsent(baseUrl, url -> {
            HttpClient httpClient = HttpClient.newBuilder()
                    .connectTimeout(properties.getConnectTimeout())
                    .build();
            JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
            factory.setReadTimeout(properties.getReadTimeout());
            return RestClient.builder()
                    .baseUrl(url)
                    .requestFactory(factory)
                    .build();
        });
    }

    @FunctionalInterface
    private interface RestClientCall<T> {
        T execute(RestClient client);
    }
}
