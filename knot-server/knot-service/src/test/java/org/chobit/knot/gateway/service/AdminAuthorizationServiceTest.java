package org.chobit.knot.gateway.service;

import org.chobit.knot.gateway.entity.AdminApiPermissionBindingEntity;
import org.chobit.knot.gateway.error.ForbiddenException;
import org.chobit.knot.gateway.error.UnauthorizedException;
import org.chobit.knot.gateway.mapper.AdminAuthorizationMapper;
import org.chobit.knot.gateway.mapper.UserMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 接口级授权采用「默认拒绝」：未配置绑定的接口一律拒绝，避免漏配被静默放行。
 */
class AdminAuthorizationServiceTest {

    private final AdminAuthorizationMapper authorizationMapper = mock(AdminAuthorizationMapper.class);
    private final UserMapper userMapper = mock(UserMapper.class);
    private final AdminAuthorizationService service =
            new AdminAuthorizationService(authorizationMapper, userMapper);

    @Test
    void missingBindingIsRejected() {
        when(authorizationMapper.getApiPermissionBinding("POST", "/api/apps")).thenReturn(null);

        ForbiddenException error = assertThrows(ForbiddenException.class,
                () -> service.validateApiAccess(1L, "POST", "/api/apps"));
        assertTrue(error.getMessage().contains("接口未配置访问权限"), error.getMessage());
    }

    @Test
    void bindingWithoutPermissionCodeIsRejected() {
        when(authorizationMapper.getApiPermissionBinding("POST", "/api/apps"))
                .thenReturn(binding("  "));

        assertThrows(ForbiddenException.class, () -> service.validateApiAccess(1L, "POST", "/api/apps"));
    }

    @Test
    void userWithoutGrantedPermissionIsRejected() {
        when(authorizationMapper.getApiPermissionBinding("POST", "/api/apps"))
                .thenReturn(binding("system:app:create"));
        when(authorizationMapper.listPermissionCodesByUserId(1L)).thenReturn(List.of("system:app:view"));

        ForbiddenException error = assertThrows(ForbiddenException.class,
                () -> service.validateApiAccess(1L, "POST", "/api/apps"));
        assertTrue(error.getMessage().contains("无权访问"), error.getMessage());
    }

    @Test
    void grantedPermissionPasses() {
        when(authorizationMapper.getApiPermissionBinding("POST", "/api/apps"))
                .thenReturn(binding("system:app:create"));
        when(authorizationMapper.listPermissionCodesByUserId(1L)).thenReturn(List.of("system:app:create"));

        assertDoesNotThrow(() -> service.validateApiAccess(1L, "POST", "/api/apps"));
    }

    @Test
    void anonymousUserIsUnauthorized() {
        assertThrows(UnauthorizedException.class, () -> service.validateApiAccess(null, "POST", "/api/apps"));
    }

    private static AdminApiPermissionBindingEntity binding(String permissionCode) {
        AdminApiPermissionBindingEntity entity = new AdminApiPermissionBindingEntity();
        entity.setId(1L);
        entity.setPermissionId(58L);
        entity.setPermissionCode(permissionCode);
        entity.setHttpMethod("POST");
        entity.setPathPattern("/api/apps");
        entity.setControllerClass("AppController");
        entity.setStatus("ENABLED");
        return entity;
    }
}
