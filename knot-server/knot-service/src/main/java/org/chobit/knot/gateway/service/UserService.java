package org.chobit.knot.gateway.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.chobit.knot.gateway.auth.JwtUtil;
import org.chobit.knot.gateway.converter.UserConverter;
import org.chobit.knot.gateway.dto.user.UserDto;
import org.chobit.knot.gateway.entity.AdminRoleEntity;
import org.chobit.knot.gateway.entity.DepartmentEntity;
import org.chobit.knot.gateway.entity.UserEntity;
import org.chobit.knot.gateway.error.BusinessException;
import org.chobit.knot.gateway.error.ErrorCode;
import org.chobit.knot.gateway.mapper.AdminAuthorizationManageMapper;
import org.chobit.knot.gateway.mapper.DepartmentMapper;
import org.chobit.knot.gateway.mapper.UserMapper;
import org.chobit.knot.gateway.model.PageRequest;
import org.chobit.knot.gateway.model.PageResult;
import org.chobit.knot.gateway.vo.auth.LoginResponse;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class UserService {
    private static final String DEFAULT_RESET_PASSWORD = "12345678";
    private static final int STATUS_ENABLED = 1;

    private final UserMapper userMapper;
    private final DepartmentMapper departmentMapper;
    private final AdminAuthorizationManageMapper roleMapper;
    private final UserConverter userConverter;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /**
     * Constructs a new instance.
     */
    public UserService(UserMapper userMapper,
                       DepartmentMapper departmentMapper,
                       AdminAuthorizationManageMapper roleMapper,
                       UserConverter userConverter) {
        this.userMapper = userMapper;
        this.departmentMapper = departmentMapper;
        this.roleMapper = roleMapper;
        this.userConverter = userConverter;
    }

    /**
     * Authenticates the user and returns the login result.
     */
    public LoginResponse login(String username, String password) {
        UserEntity user = userMapper.getUserByUsername(username);
        if (user == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户名或密码错误");
        }
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户名或密码错误");
        }
        if (!statusEnabledEquals(user.getStatus())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "当前用户已被禁用");
        }

        userMapper.updateLastLoginTime(user.getId());
        if (passwordEncoder.matches(DEFAULT_RESET_PASSWORD, user.getPasswordHash())) {
            String passwordChangeToken = JwtUtil.generatePasswordChangeToken(user.getId(), user.getUsername());
            return LoginResponse.forcePasswordChange(
                    user.getId(),
                    user.getUsername(),
                    user.getRealName(),
                    passwordChangeToken
            );
        }

        List<String> roles = userMapper.listRoleCodesByUserId(user.getId());
        String token = JwtUtil.generateToken(user.getId(), user.getUsername(), roles);
        return LoginResponse.access(token, user.getId(), user.getUsername(), user.getRealName(), roles);
    }

    /**
     * Completes the forced password change flow.
     */
    @Transactional
    public void forcePasswordChange(String passwordChangeToken, String newPassword) {
        String password = newPassword == null ? "" : newPassword.trim();
        if (password.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "新密码不能为空");
        }
        if (DEFAULT_RESET_PASSWORD.equals(password)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "新密码不能与默认密码相同");
        }

        Long userId;
        try {
            userId = JwtUtil.parsePasswordChangeToken(passwordChangeToken).get("userId", Long.class);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "改密凭证已失效，请重新登录");
        }

        UserEntity user = userMapper.getUserById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        if (!statusEnabledEquals(user.getStatus())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "当前用户已被禁用");
        }
        userMapper.updateUserPassword(userId, passwordEncoder.encode(password));
    }

    /**
     * Lists matching results.
     */
    public PageResult<UserDto> listUsers(PageRequest pageRequest) {
        return listUsers(pageRequest, null);
    }

    /**
     * Lists matching results.
     */
    public PageResult<UserDto> listUsers(PageRequest pageRequest, String keyword) {
        PageHelper.startPage(pageRequest.pageNum(), pageRequest.pageSize());
        PageInfo<UserEntity> pageInfo = new PageInfo<>(userMapper.listUsers(normalizeKeyword(keyword)));
        attachRoleBindings(pageInfo.getList());
        return PageResult.fromPage(pageInfo, userConverter::toDtoList, pageRequest);
    }

    private static String normalizeKeyword(String keyword) {
        String value = keyword != null ? keyword.trim() : "";
        return value.isEmpty() ? null : value;
    }

    private void attachRoleBindings(List<UserEntity> users) {
        if (users == null || users.isEmpty()) {
            return;
        }
        List<Long> userIds = users.stream()
                .map(UserEntity::getId)
                .filter(Objects::nonNull)
                .toList();
        if (userIds.isEmpty()) {
            return;
        }
        Map<Long, List<String>> roleNamesByUserId = userMapper.listRoleNamesByUserIds(userIds).stream()
                .filter(item -> item.get("userId") != null && item.get("roleName") != null)
                .collect(Collectors.groupingBy(
                        item -> ((Number) item.get("userId")).longValue(),
                        LinkedHashMap::new,
                        Collectors.mapping(item -> String.valueOf(item.get("roleName")), Collectors.toList())
                ));
        users.forEach(user -> {
            user.setRoleIds(userMapper.listRoleIdsByUserId(user.getId()));
            user.setRoleNames(roleNamesByUserId.getOrDefault(user.getId(), List.of()));
        });
    }

    /**
     * Returns a user snapshot for operation log auditing.
     */
    public Map<String, Object> userAuditSnapshot(Long id) {
        if (id == null) {
            return null;
        }
        UserEntity entity = userMapper.getUserById(id);
        if (entity == null) {
            return null;
        }
        entity.setRoleIds(userMapper.listRoleIdsByUserId(id));
        entity.setRoleNames(resolveRoleNames(entity.getRoleIds()));
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", entity.getId());
        snapshot.put("username", entity.getUsername());
        snapshot.put("realName", entity.getRealName());
        snapshot.put("deptId", entity.getDeptId());
        snapshot.put("deptName", entity.getDeptName());
        snapshot.put("status", entity.getStatus());
        snapshot.put("roleIds", entity.getRoleIds());
        snapshot.put("roleNames", entity.getRoleNames());
        snapshot.put("lastLoginTime", entity.getLastLoginTime());
        snapshot.put("updatedAt", entity.getUpdatedAt());
        return snapshot;
    }

    /**
     * Creates a new user.
     */
    @Transactional
    public UserDto createUser(UserDto request) {
        DepartmentEntity department = validateDepartment(request.deptId());
        List<Long> roleIds = normalizeRoleIds(request.roleIds());
        String password = request.password() == null ? "" : request.password().trim();
        if (password.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "密码不能为空");
        }

        UserEntity entity = new UserEntity();
        entity.setUsername(request.username());
        entity.setRealName(request.realName());
        entity.setDeptId(department != null ? department.getId() : null);
        entity.setDeptName(department != null ? department.getDeptName() : null);
        entity.setStatus(request.status());
        entity.setPasswordHash(passwordEncoder.encode(password));
        userMapper.insertUser(entity);
        saveUserRoles(entity.getId(), roleIds);
        return loadUserDto(entity.getId());
    }

    /**
     * Updates the user status.
     */
    @Transactional
    public UserDto updateUserStatus(Long id, String status) {
        UserEntity entity = userMapper.getUserById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        entity.setStatus(Integer.parseInt(status));
        userMapper.updateUserStatus(entity);
        return loadUserDto(id);
    }

    /**
     * Updates the user profile.
     */
    @Transactional
    public UserDto updateUser(UserDto request) {
        UserEntity entity = userMapper.getUserById(request.id());
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        DepartmentEntity department = validateDepartment(request.deptId());
        List<Long> roleIds = normalizeRoleIds(request.roleIds());
        entity.setRealName(request.realName());
        entity.setDeptId(department != null ? department.getId() : null);
        entity.setDeptName(department != null ? department.getDeptName() : null);
        entity.setStatus(request.status());
        userMapper.updateUser(entity);
        saveUserRoles(entity.getId(), roleIds);
        return loadUserDto(entity.getId());
    }

    /**
     * Resets the target user's password to the default value.
     */
    @Transactional
    public UserDto resetPassword(Long id) {
        UserEntity entity = userMapper.getUserById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        userMapper.updateUserPassword(id, passwordEncoder.encode(DEFAULT_RESET_PASSWORD));
        return loadUserDto(id);
    }

    private UserDto loadUserDto(Long id) {
        UserEntity user = userMapper.getUserById(id);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        List<Long> roleIds = userMapper.listRoleIdsByUserId(id);
        user.setRoleIds(roleIds);
        user.setRoleNames(resolveRoleNames(roleIds));
        return userConverter.toDto(user);
    }

    private void saveUserRoles(Long userId, List<Long> roleIds) {
        userMapper.deleteUserRoles(userId);
        if (!roleIds.isEmpty()) {
            userMapper.insertUserRoles(userId, roleIds);
        }
    }

    private List<Long> normalizeRoleIds(List<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return List.of();
        }
        List<Long> normalizedIds = roleIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (normalizedIds.isEmpty()) {
            return List.of();
        }
        List<AdminRoleEntity> matchedRoles = roleMapper.listRoles(null).stream()
                .filter(role -> normalizedIds.contains(role.getId()))
                .toList();
        Set<Long> matchedIds = matchedRoles.stream().map(AdminRoleEntity::getId).collect(Collectors.toCollection(LinkedHashSet::new));
        if (matchedIds.size() != normalizedIds.size()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "存在无效的角色绑定");
        }
        return normalizedIds;
    }

    private List<String> resolveRoleNames(List<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return List.of();
        }
        Map<Long, String> roleNameMap = roleMapper.listRoles(null).stream()
                .collect(Collectors.toMap(AdminRoleEntity::getId, AdminRoleEntity::getName, (left, right) -> left, LinkedHashMap::new));
        return roleIds.stream()
                .map(roleNameMap::get)
                .filter(Objects::nonNull)
                .toList();
    }

    private DepartmentEntity validateDepartment(Long deptId) {
        if (deptId == null) {
            return null;
        }
        DepartmentEntity department = departmentMapper.getById(deptId);
        if (department == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "部门不存在");
        }
        return department;
    }

    private static boolean statusEnabledEquals(Integer status) {
        return status != null && status == STATUS_ENABLED;
    }
}
