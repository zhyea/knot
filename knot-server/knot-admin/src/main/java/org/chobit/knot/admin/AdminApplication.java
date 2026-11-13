package org.chobit.knot.admin;

import org.chobit.knot.dal.mapper.AppMapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * 管理端启动类。
 * {@code scanBasePackages} 上提到 {@code org.chobit.knot}，覆盖 admin / svc / adapter / plugin 四个平行顶级包。
 * {@code @MapperScan} 用 {@code basePackageClasses} 而非字符串，包迁移时参与编译校验。
 */
@EnableAsync
@EnableTransactionManagement
@SpringBootApplication(scanBasePackages = "org.chobit.knot")
@MapperScan(basePackageClasses = AppMapper.class)
public class AdminApplication {

    /**
     * Application entry point.
     */
    public static void main(String[] args) {
        SpringApplication.run(AdminApplication.class, args);
    }
}
