package org.chobit.knot.gateway.service;

import org.chobit.knot.gateway.entity.AdminApiPermissionBindingEntity;
import org.chobit.knot.gateway.entity.AdminMenuEntity;
import org.chobit.knot.gateway.entity.UserEntity;
import org.chobit.knot.gateway.error.UnauthorizedException;
import org.chobit.knot.gateway.mapper.AdminAuthorizationMapper;
import org.chobit.knot.gateway.mapper.UserMapper;
import org.chobit.knot.gateway.vo.auth.AdminAuthorizationInfoResponse;
import org.chobit.knot.gateway.vo.auth.AdminMenuItem;
import org.chobit.knot.gateway.vo.auth.AdminModuleItem;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class AdminAuthorizationService {

    private final AdminAuthorizationMapper adminAuthorizationMapper;
    private final UserMapper userMapper;

    /**
     * Constructs a new instance.
     */
    public AdminAuthorizationService(AdminAuthorizationMapper adminAuthorizationMapper,
                                     UserMapper userMapper) {
        this.adminAuthorizationMapper = adminAuthorizationMapper;
        this.userMapper = userMapper;
    }

    /**
     * Validates whether the current user can access the target admin api.
     */
    public void validateApiAccess(Long userId, String httpMethod, String pathPattern) {
        if (userId == null) {
            throw new UnauthorizedException("未获取到当前登录用户信息");
        }
        AdminApiPermissionBindingEntity binding = adminAuthorizationMapper.getApiPermissionBinding(httpMethod, pathPattern);
        if (binding == null || binding.getPermissionCode() == null || binding.getPermissionCode().isBlank()) {
            throw new UnauthorizedException("接口未配置访问权限: " + httpMethod + " " + pathPattern);
        }
        if (!listPermissionCodes(userId).contains(binding.getPermissionCode())) {
            throw new UnauthorizedException("当前用户无权访问该接口: " + httpMethod + " " + pathPattern);
        }
    }

    /**
     * Returns the current user's authorization information.
     */
    public AdminAuthorizationInfoResponse getAuthorizationInfo(Long userId) {
        UserEntity user = userMapper.getUserById(userId);
        if (user == null) {
            throw new UnauthorizedException("当前登录用户不存在或已删除");
        }
        return new AdminAuthorizationInfoResponse(
                user.getId(),
                user.getUsername(),
                user.getRealName(),
                user.getDeptId(),
                user.getDeptName(),
                userMapper.listRoleCodesByUserId(userId),
                listPermissionCodes(userId),
                buildModules(adminAuthorizationMapper.listMenusByUserId(userId))
        );
    }

    /**
     * Lists permission codes granted to the specified user.
     */
    public List<String> listPermissionCodes(Long userId) {
        return adminAuthorizationMapper.listPermissionCodesByUserId(userId).stream()
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
    }

    private List<AdminModuleItem> buildModules(List<AdminMenuEntity> menus) {
        Map<Long, List<AdminMenuEntity>> menusByModuleId = new LinkedHashMap<>();
        Map<Long, AdminMenuEntity> moduleRefs = new LinkedHashMap<>();
        for (AdminMenuEntity menu : menus) {
            moduleRefs.putIfAbsent(menu.getModuleId(), menu);
            menusByModuleId.computeIfAbsent(menu.getModuleId(), key -> new ArrayList<>()).add(menu);
        }
        List<AdminModuleItem> result = new ArrayList<>(menusByModuleId.size());
        for (Map.Entry<Long, List<AdminMenuEntity>> entry : menusByModuleId.entrySet()) {
            AdminMenuEntity moduleRef = moduleRefs.get(entry.getKey());
            result.add(new AdminModuleItem(
                    entry.getKey(),
                    moduleRef.getModuleCode(),
                    moduleRef.getModuleName(),
                    moduleRef.getModuleIcon(),
                    moduleRef.getModuleSortOrder(),
                    buildMenuTree(entry.getValue())
            ));
        }
        return result;
    }

    private List<AdminMenuItem> buildMenuTree(List<AdminMenuEntity> menus) {
        Map<Long, AdminMenuEntity> menuById = new LinkedHashMap<>();
        Map<Long, List<AdminMenuEntity>> childrenByParentId = new LinkedHashMap<>();
        List<AdminMenuEntity> roots = new ArrayList<>();
        for (AdminMenuEntity menu : menus) {
            menuById.put(menu.getId(), menu);
        }
        for (AdminMenuEntity menu : menus) {
            if (menu.getParentId() == null || !menuById.containsKey(menu.getParentId())) {
                roots.add(menu);
            } else {
                childrenByParentId.computeIfAbsent(menu.getParentId(), key -> new ArrayList<>()).add(menu);
            }
        }
        return roots.stream()
                .map(menu -> toMenuItem(menu, childrenByParentId))
                .toList();
    }

    private AdminMenuItem toMenuItem(AdminMenuEntity menu,
                                     Map<Long, List<AdminMenuEntity>> childrenByParentId) {
        List<AdminMenuItem> children = childrenByParentId.getOrDefault(menu.getId(), List.of()).stream()
                .map(child -> toMenuItem(child, childrenByParentId))
                .toList();
        return new AdminMenuItem(
                menu.getId(),
                menu.getParentId(),
                menu.getMenuCode(),
                menu.getMenuName(),
                menu.getRoutePath(),
                menu.getComponentKey(),
                menu.getIcon(),
                menu.getSortOrder(),
                children
        );
    }
}
