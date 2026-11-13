package org.chobit.knot.gateway;

import org.chobit.knot.dal.mapper.AppMapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 网关启动类。
 * {@code scanBasePackages} 与 {@code @MapperScan} 的 {@code basePackageClasses} 覆盖 {@code .gateway}/{@code .svc}/{@code .adapter}/{@code .plugin} 四个顶级包。
 */
@SpringBootApplication(scanBasePackages = "org.chobit.knot")
@MapperScan(basePackageClasses = AppMapper.class)
@EnableAsync
public class GatewayApplication {

    /**
     * Application entry point.
     */
    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
