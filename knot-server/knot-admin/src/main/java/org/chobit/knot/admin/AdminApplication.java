package org.chobit.knot.admin;

import org.chobit.knot.dal.mapper.AppMapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * 管理端启动类。
 *
 * <p><b>为什么 {@code scanBasePackages} 上提到 {@code org.chobit.knot}</b>：多模块分层后
 * 组件分布在 {@code org.chobit.knot.admin}（本模块）、{@code .svc}（knot-service）、
 * {@code .adapter}（knot-adapter）、{@code .plugin}（knot-plugin）四个平行顶级包里，
 * 而 {@code @SpringBootApplication} 默认只扫启动类所在包及其子包。不上提则 svc/adapter/plugin
 * 的 Bean 全部注册不上，控制器注入失败、上下文启动即挂。</p>
 *
 * <p>上提到 {@code org.chobit.knot}（而非逐个列举 {@code .admin/.svc/.adapter/.plugin}）的理由：
 * 顶级 root 包名不会随模块增删漂移，不必每加一个模块就回来改一遍。本模块不与
 * knot-gateway 互相依赖，故不会误扫到网关组件。</p>
 *
 * <p><b>为什么 {@code @MapperScan} 用 {@code basePackageClasses} 而非字符串</b>：
 * 字符串形式在包迁移时不参与编译校验——曾静默指向已不存在的
 * {@code org.chobit.knot.gateway.mapper}，导致一个 Mapper 都注册不上而编译全绿。
 * 改用类引用后，包一旦再迁移，编译期立即报错。</p>
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
