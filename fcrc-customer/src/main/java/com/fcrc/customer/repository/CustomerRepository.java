package com.fcrc.customer.repository;

import com.fcrc.customer.domain.CustomerEntity;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 客户数据访问层。查询语句使用参数绑定，杜绝字符串拼接 SQL 注入。 */
public interface CustomerRepository extends JpaRepository<CustomerEntity, Long> {

    Optional<CustomerEntity> findByCustomerNo(String customerNo);

    boolean existsByCustomerNo(String customerNo);

    long countByPepTrue();

    long countByKycStatus(String status);

    @Query("""
            select c from CustomerEntity c
            where (:keyword is null
                   or lower(c.name) like lower(concat('%', :keyword, '%'))
                   or lower(c.customerNo) like lower(concat('%', :keyword, '%')))
              and (:status is null or c.kycStatus = :status)
            """)
    Page<CustomerEntity> search(@Param("keyword") String keyword,
                                @Param("status") String status,
                                Pageable pageable);
}
