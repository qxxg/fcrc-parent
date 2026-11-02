package com.fcrc.customer.dto;

import com.fcrc.common.domain.CustomerProfile;

/**
 * 内部客户画像响应。
 *
 * <p>只有风控、合规等内网服务需要原文 PII。该响应带 customerNo，便于把评估结果关联回客户。
 * 教学项目中为了聚焦架构未实现鉴权，生产环境必须补充服务身份认证与访问审计。</p>
 */
public record InternalCustomerProfile(String customerNo, CustomerProfile profile) {
}
