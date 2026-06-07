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
     * Executes the public operation. Executes the public operation.
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

        // 更新最后登录时间
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
     * 操作审计快照，不包含密码字段，供
     * {@link org.chobit.knot.gateway.annotation.OperationLog} 的 SpEL 使用。
     */
    public Map<String, Object> userAuditSnapshot(Long id) {
        if (id == null) {
            return null;
        }
        UserEntity entity = userMapper.getUserById(id);
        if (entity == null) {
            return null;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", entity.getId());
        m.put("username", entity.getUsername());
        m.put("realName", entity.getRealName());
        m.put("deptId", entity.getDeptId());
        m.put("deptName", entity.getDeptName());
        m.put("status", entity.getStatus());
        m.put("lastLoginTime", entity.getLastLoginTime());
        m.put("updatedAt", entity.getUpdatedAt());
        return m;
    }

    /**
     * Creates a new resource. Executes the public operation.
     */
    @Transactional
    public UserDto createUser(UserDto request) {
        DepartmentEntity department = validateDepartment(request.deptId());
        UserEntity entity = new UserEntity();
        entity.setUsername(request.username());
        entity.setRealName(request.realName());
        entity.setDeptId(department != null ? department.getId() : null);
        entity.setDeptName(department != null ? department.getDeptName() : null);
        entity.setStatus(request.status());
        // 加密密码
        if (request.password() != null && !request.password().isEmpty()) {
            entity.setPasswordHash(passwordEncoder.encode(request.password()));
        } else {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "密码不能为空");
        }
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
            throw new BusinessException(ErrorCode.NOT_FOUND, "user not found");
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
            throw new BusinessException(ErrorCode.NOT_FOUND, "user not found");
        }
        DepartmentEntity department = validateDepartment(request.deptId());
        entity.setRealName(request.realName());
        entity.setDeptId(department != null ? department.getId() : null);
        entity.setDeptName(department != null ? department.getDeptName() : null);
        entity.setStatus(request.status());
        // 如果提供了密码则一并更新
        if (request.password() != null && !request.password().isEmpty()) {
            entity.setPasswordHash(passwordEncoder.encode(request.password()));
        }
        userMapper.updateUser(entity);
        return userConverter.toDto(entity);
    }

    private DepartmentEntity validateDepartment(Long deptId) {
        if (deptId == null) {
            return null;
        }
        DepartmentEntity department = departmentMapper.getById(deptId);
        if (department == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "department not found");
        }
        return department;
    }
}
