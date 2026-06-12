package org.chobit.knot.gateway.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.chobit.knot.gateway.auth.JwtUtil;
import org.chobit.knot.gateway.converter.UserConverter;
import org.chobit.knot.gateway.dto.user.UserDto;
import org.chobit.knot.gateway.entity.DepartmentEntity;
import org.chobit.knot.gateway.entity.UserEntity;
import org.chobit.knot.gateway.error.BusinessException;
import org.chobit.knot.gateway.error.ErrorCode;
import org.chobit.knot.gateway.mapper.DepartmentMapper;
import org.chobit.knot.gateway.mapper.UserMapper;
import org.chobit.knot.gateway.model.PageRequest;
import org.chobit.knot.gateway.model.PageResult;
import org.chobit.knot.gateway.vo.auth.LoginResponse;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class UserService {
    private static final String DEFAULT_RESET_PASSWORD = "12345678";

    private final UserMapper userMapper;
    private final DepartmentMapper departmentMapper;
    private final UserConverter userConverter;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /**
     * Constructs a new instance.
     */
    public UserService(UserMapper userMapper, DepartmentMapper departmentMapper, UserConverter userConverter) {
        this.userMapper = userMapper;
        this.departmentMapper = departmentMapper;
        this.userConverter = userConverter;
    }

    /**
     * Authenticates the user and returns a login response.
     */
    public LoginResponse login(String username, String password) {
        UserEntity user = userMapper.getUserByUsername(username);
        if (user == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户名或密码错误");
        }

        boolean matches = passwordEncoder.matches(password, user.getPasswordHash());
        if (!matches) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户名或密码错误");
        }

        userMapper.updateLastLoginTime(user.getId());

        List<String> roles = userMapper.listRoleCodesByUserId(user.getId());
        String token = JwtUtil.generateToken(user.getId(), user.getUsername(), roles);
        return new LoginResponse(token, user.getId(), user.getUsername(), user.getRealName(), roles);
    }

    /**
     * Lists matching results. Executes the public operation.
     */
    public PageResult<UserDto> listUsers(PageRequest pageRequest) {
        return listUsers(pageRequest, null);
    }

    /**
     * Lists matching results. Executes the public operation.
     */
    public PageResult<UserDto> listUsers(PageRequest pageRequest, String keyword) {
        PageHelper.startPage(pageRequest.pageNum(), pageRequest.pageSize());
        PageInfo<UserEntity> pageInfo = new PageInfo<>(userMapper.listUsers(normalizeKeyword(keyword)));
        return PageResult.fromPage(pageInfo, userConverter::toDtoList, pageRequest);
    }

    private static String normalizeKeyword(String keyword) {
        String value = keyword != null ? keyword.trim() : "";
        return value.isEmpty() ? null : value;
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
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", entity.getId());
        snapshot.put("username", entity.getUsername());
        snapshot.put("realName", entity.getRealName());
        snapshot.put("deptId", entity.getDeptId());
        snapshot.put("deptName", entity.getDeptName());
        snapshot.put("status", entity.getStatus());
        snapshot.put("lastLoginTime", entity.getLastLoginTime());
        snapshot.put("updatedAt", entity.getUpdatedAt());
        return snapshot;
    }

    /**
     * Creates a new resource. Executes the public operation.
     */
    @Transactional
    public UserDto createUser(UserDto request) {
        DepartmentEntity department = validateDepartment(request.deptId());
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
        return userConverter.toDto(entity);
    }

    /**
     * Updates the target resource. Executes the public operation.
     */
    @Transactional
    public UserDto updateUserStatus(Long id, String status) {
        UserEntity entity = userMapper.getUserById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        entity.setStatus(Integer.parseInt(status));
        userMapper.updateUserStatus(entity);
        return userConverter.toDto(entity);
    }

    /**
     * Updates the target resource. Executes the public operation.
     */
    @Transactional
    public UserDto updateUser(UserDto request) {
        UserEntity entity = userMapper.getUserById(request.id());
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        DepartmentEntity department = validateDepartment(request.deptId());
        entity.setRealName(request.realName());
        entity.setDeptId(department != null ? department.getId() : null);
        entity.setDeptName(department != null ? department.getDeptName() : null);
        entity.setStatus(request.status());
        userMapper.updateUser(entity);
        return userConverter.toDto(entity);
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
        return userConverter.toDto(userMapper.getUserById(id));
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
}
