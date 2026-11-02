package com.fcrc.common.util;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 业务编号生成器。
 *
 * <p>教学项目使用“前缀 + 日期 + 6 位随机数”，可读性好。生产环境应使用雪花算法或数据库序列，
 * 并把唯一性约束放在数据库层，避免依赖随机数的低概率碰撞。</p>
 */
public final class BusinessIds {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final DateTimeFormatter DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private BusinessIds() {
    }

    public static String next(String prefix) {
        int suffix = RANDOM.nextInt(1_000_000);
        return prefix + "-" + LocalDate.now().format(DATE) + "-" + String.format("%06d", suffix);
    }
}
