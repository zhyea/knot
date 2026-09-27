package org.chobit.knot.gateway.auth;

import org.chobit.knot.gateway.GlobalExceptionHandler;
import org.chobit.knot.gateway.error.ForbiddenException;
import org.chobit.knot.gateway.rw.RwProperties;
import org.chobit.knot.gateway.service.AdminAuthorizationService;
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
 * 拦截器默认拒绝：未标注任何权限注解的 handler 也要走接口级授权，被拒时返回 403。
 * <p>
 * 用桩类而非 Mockito mock：JDK 25 下 inline mock maker 无法改写具体类。
 */
class AdminAuthorizationInterceptorMvcTest {

    private final StubAuthorizationService authorizationService = new StubAuthorizationService();
    private final CurrentAuth currentAuth = new CurrentAuth() {
        @Override
        public Long currentUserId() {
            return 1L;
        }
    };

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler(new RwProperties()))
                .addInterceptors(new AdminAuthorizationInterceptor(authorizationService, currentAuth))
                .build();
    }

    @Test
    void handlerWithoutAuthCheckAnnotationIsStillValidated() throws Exception {
        authorizationService.setReject(true);

        mockMvc.perform(post("/api/ping"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void grantedRequestPassesThrough() throws Exception {
        authorizationService.setReject(false);

        mockMvc.perform(post("/api/ping"))
                .andExpect(status().isOk());
    }

    private static class StubAuthorizationService extends AdminAuthorizationService {
        private boolean reject;

        StubAuthorizationService() {
            super(null, null);
        }

        void setReject(boolean reject) {
            this.reject = reject;
        }

        @Override
        public void validateApiAccess(Long userId, String httpMethod, String pathPattern) {
            if (reject) {
                throw new ForbiddenException("当前用户无权访问该接口: " + httpMethod + " " + pathPattern);
            }
        }
    }

    @RestController
    static class TestController {
        @PostMapping("/api/ping")
        String ping() {
            return "pong";
        }
    }
}
