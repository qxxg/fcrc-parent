package com.fcrc.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 客户中心契约测试：验证建档、脱敏、参数校验三条关键路径。
 *
 * <p>测试使用随机内存库，避免污染本地演示数据。</p>
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:customer-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
@ActiveProfiles("local")
class CustomerApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void createsCustomerAndMasksSensitiveFields() throws Exception {
        mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "测试客户",
                                  "customerType": "INDIVIDUAL",
                                  "idNumber": "110101199001011234",
                                  "phone": "13812345678",
                                  "email": "tester@example.com",
                                  "country": "China",
                                  "annualIncome": 100000,
                                  "occupation": "工程师",
                                  "pep": false,
                                  "channel": "WEB"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.customerNo").value(org.hamcrest.Matchers.startsWith("CUS-")))
                .andExpect(jsonPath("$.data.name").value("测**户"))
                .andExpect(jsonPath("$.data.phone").value("138****5678"));
    }

    @Test
    void rejectsInvalidCustomerType() throws Exception {
        mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "错误类型", "customerType": "ALIEN", "country": "China"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("40001"));
    }

    @Test
    void listsSeededCustomers() throws Exception {
        mockMvc.perform(get("/api/customers?size=100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(org.hamcrest.Matchers.greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.data.items").isArray());
    }
}
