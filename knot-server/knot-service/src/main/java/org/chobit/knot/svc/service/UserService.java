package org.chobit.knot.svc.service;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.chobit.knot.common.auth.JwtKit;
import org.chobit.knot.svc.converter.UserConverter;
import org.chobit.knot.svc.dto.user.UserDto;
import org.chobit.knot.dal.entity.AdminRoleEntity;
import org.chobit.knot.dal.entity.DepartmentEntity;
import org.chobit.knot.dal.entity.UserEntity;
import org.chobit.knot.common.error.BusinessException;
import org.chobit.knot.common.error.ErrorCode;
import org.chobit.knot.dal.mapper.AdminAuthorizationManageMapper;
import org.chobit.knot.dal.mapper.DepartmentMapper;
import org.chobit.knot.dal.mapper.UserMapper;
import org.chobit.knot.common.model.PageRequest;
import org.chobit.knot.common.model.PageResult;
import org.chobit.knot.svc.service.support.CodeBindingSupport;
import org.chobit.knot.svc.vo.auth.LoginResponse;
import org.chobit.knot.svc.service.support.AuditSnapshotSupport;
import org.chobit.knot.common.util.JsonKit;
import org.chobit.knot.svc.vo.user.ResetPasswordResult;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.security.SecureRandom;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class UserService {
    private static final int STATUS_ENABLED = 1;

    /** 一次性口令字符集：去掉 0/O/1/l/I 等易混淆字符。 */
    private static final char[] PASSWORD_CHARS =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789".toCharArray();
    private static final int ONE_TIME_PASSWORD_LENGTH = 12;
    private static final SecureRandom RANDOM = new SecureRandom();

    /** BCrypt 输入上限：超过 72 字节的部分会被截断，入库前显式拒绝。 */
    private static final int MAX_PASSWORD_BYTES = 72;

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
        if (passwordTooLong(password) || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户名或密码错误");
        }
        if (isDisabled(user.getStatus())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "当前用户已被禁用");
        }

        userMapper.updateLastLoginTime(user.getId());

        List<String> roles = userMapper.listRoleCodesByUserId(user.getId());
        String token = JwtKit.generateToken(user.getId(), user.getUsername(), roles);
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
        if (passwordTooLong(password)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "密码长度不能超过 72 字节");
        }
        Long userId;
        try {
            userId = JwtKit.parsePasswordChangeToken(passwordChangeToken).get("userId", Long.class);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "改密凭证已失效，请重新登录");
        }

        UserEntity user = userMapper.getUserById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        if (isDisabled(user.getStatus())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "当前用户已被禁用");
        }
        userMapper.updateUserPassword(userId, passwordEncoder.encode(password));
    }

    /**
     * Lists matching results.
     */
    public PageResult<UserDto> listUsers(PageRequest pageRequest, String keyword) {
        try (Page<?> ignored = PageHelper.startPage(pageRequest.pageNum(), pageRequest.pageSize())) {
            PageInfo<UserEntity> pageInfo = new PageInfo<>(userMapper.listUsers(normalizeKeyword(keyword)));
            attachRoleBindings(pageInfo.getList());
            return PageResult.fromPage(pageInfo, userConverter::toDtoList, pageRequest);
        }
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
     * 操作日志用的用户快照：{@code @OperationLog} 的 SpEL 在方法前后各取一次，
     * 用户不存在时返回 {@code null}。角色随快照一并带出，密码等敏感字段脱敏后落日志。
     *
     * <p>仅由 SpEL 反射调用，Java 侧无直接引用，IDE 会误报 unused。</p>
     */
    @SuppressWarnings("unused")
    public Map<String, Object> userAuditSnapshot(Long id) {
        return AuditSnapshotSupport.capture(id, key -> {
            UserEntity entity = userMapper.getUserById(key);
            if (entity == null) {
                return null;
            }
            entity.setRoleIds(userMapper.listRoleIdsByUserId(key));
            entity.setRoleNames(resolveRoleNames(entity.getRoleIds()));
            return JsonKit.toMaskedMap(entity);
        });
    }

    /**
     * Creates a new user.
     */
    @Transactional
    public UserDto createUser(UserDto request) {
        DepartmentEntity department = validateDepartment(request.deptCode());
        List<Long> roleIds = normalizeRoleIds(request.roleIds());
        String password = request.password() == null ? "" : request.password().trim();
        if (password.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "密码不能为空");
        }
        if (passwordTooLong(password)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "密码长度不能超过 72 字节");
        }

        UserEntity entity = new UserEntity();
        entity.setUsername(request.username());
        entity.setRealName(request.realName());
        entity.setDeptCode(department != null ? department.getDeptCode() : null);
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
    public UserDto updateUserStatus(Long id, Integer status) {
        UserEntity entity = userMapper.getUserById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        entity.setStatus(status);
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
        String username = resolveUsernameForUpdate(entity, request.username());
        DepartmentEntity department = validateDepartment(request.deptCode());
        List<Long> roleIds = normalizeRoleIds(request.roleIds());
        entity.setUsername(username);
        entity.setRealName(request.realName());
        entity.setDeptCode(department != null ? department.getDeptCode() : null);
        entity.setDeptName(department != null ? department.getDeptName() : null);
        entity.setStatus(request.status());
        userMapper.updateUser(entity);
        saveUserRoles(entity.getId(), roleIds);
        return loadUserDto(entity.getId());
    }

    /**
     * 用户编码（登录名）改码：未绑定（应用负责人 / 消费者归属 / 路由规则归属）时可改，
     * 已绑定直接 409；改码前做唯一性校验（uk_users_username 不区分 is_deleted，故含已删除行）。
     *
     * <p>入参 username 为空一律拒绝：登录名是必填项，不做「不修改」特例。</p>
     */
    private String resolveUsernameForUpdate(UserEntity entity, String requested) {
        String username = CodeBindingSupport.normalize(requested);
        if (username.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "登录名不能为空");
        }
        CodeBindingSupport.requireCodeChangeAllowed(
                entity.getUsername(), username, entity.getBoundCount(), "用户编码");
        if (!CodeBindingSupport.isSameCode(entity.getUsername(), username)
                && userMapper.getUserByUsername(username) != null) {
            throw new BusinessException(ErrorCode.CONFLICT, "登录名「" + username + "」已被占用");
        }
        return username;
    }

    /**
     * Resets the target user's password to a random one-time value.
     */
    @Transactional
    public ResetPasswordResult resetPassword(Long id) {
        UserEntity entity = userMapper.getUserById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        String oneTimePassword = generateOneTimePassword();
        userMapper.updateUserPassword(id, passwordEncoder.encode(oneTimePassword));
        return new ResetPasswordResult(entity.getId(), entity.getUsername(), oneTimePassword);
    }

    private static String generateOneTimePassword() {
        StringBuilder builder = new StringBuilder(ONE_TIME_PASSWORD_LENGTH);
        for (int i = 0; i < ONE_TIME_PASSWORD_LENGTH; i++) {
            builder.append(PASSWORD_CHARS[RANDOM.nextInt(PASSWORD_CHARS.length)]);
        }
        return builder.toString();
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

    private DepartmentEntity validateDepartment(String deptCode) {
        if (deptCode == null || deptCode.isBlank()) {
            return null;
        }
        DepartmentEntity department = departmentMapper.getByCode(deptCode);
        if (department == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "部门不存在");
        }
        return department;
    }

    private static boolean isDisabled(Integer status) {
        return status == null || status != STATUS_ENABLED;
    }

    private static boolean passwordTooLong(String password) {
        return password != null && password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES;
    }
}
