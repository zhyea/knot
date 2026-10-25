package org.chobit.knot.gateway;

import org.chobit.knot.dal.mapper.AppMapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 网关启动类。
 *
 * <p>{@code scanBasePackages} 上提与 {@code @MapperScan} 改用 {@code basePackageClasses}
 * 的理由同 {@code AdminApplication}：组件分布在 {@code .gateway}/{@code .svc}/
 * {@code .adapter}/{@code .plugin} 四个平行顶级包，默认扫描范围只覆盖 {@code .gateway}。
 * 本模块不与 knot-admin 互相依赖，故不会误扫管理端组件。</p>
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
