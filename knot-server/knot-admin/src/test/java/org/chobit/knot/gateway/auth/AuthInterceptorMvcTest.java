package org.chobit.knot.gateway.auth;

import org.chobit.knot.gateway.GlobalExceptionHandler;
import org.chobit.knot.gateway.rw.RwProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 验证：拦截器 preHandle 抛出的 {@link org.chobit.knot.gateway.error.UnauthorizedException}
 * 会由 {@link GlobalExceptionHandler} 处理并返回 401 + ApiResponse。
 */
class AuthInterceptorMvcTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler(new RwProperties()))
                .addInterceptors(new AuthInterceptor())
                .build();
    }

    @Test
    void noToken_returns401AndApiResponse() throws Exception {
        mockMvc.perform(post("/api/ping"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("未登录或 token 无效"));
    }

    @RestController
    static class TestController {
        @PostMapping("/api/ping")
        String ping() {
            return "pong";
        }
    }
}
