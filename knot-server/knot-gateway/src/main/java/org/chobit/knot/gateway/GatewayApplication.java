package org.chobit.knot.gateway;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.web.client.RestClient;

@SpringBootApplication
@MapperScan("org.chobit.knot.gateway.mapper")
@EnableAsync
public class GatewayApplication {

    /**
     * Application entry point.
     */
    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    @Bean
    public RestClient restClient(RestClient.Builder builder) {
        return builder.build();
    }
}
