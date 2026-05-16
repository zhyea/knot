package org.chobit.knot.gateway.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.chobit.knot.gateway.auth.JwtUtil;
import org.chobit.knot.gateway.converter.UserConverter;
import org.chobit.knot.gateway.dto.user.UserDto;
import org.chobit.knot.gateway.entity.UserEntity;
import org.chobit.knot.gateway.error.BusinessException;
import org.chobit.knot.gateway.error.ErrorCode;
import org.chobit.knot.gateway.mapper.UserMapper;
import org.chobit.knot.gateway.model.PageRequest;
import org.chobit.knot.gateway.model.PageResult;
import org.chobit.knot.gateway.vo.auth.LoginResponse;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class UserService {
    private final UserMapper userMapper;
    private final UserConverter userConverter;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public UserService(UserMapper userMapper, UserConverter userConverter) {
        this.userMapper = userMapper;
        this.userConverter = userConverter;
    }

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
        
        String token = JwtUtil.generateToken(user.getId(), user.getUsername());
        return new LoginResponse(token, user.getId(), user.getUsername(), user.getRealName());
    }

    public PageResult<UserDto> listUsers(PageRequest pageRequest) {
        PageHelper.startPage(pageRequest.pageNum(), pageRequest.pageSize());
        PageInfo<UserEntity> pageInfo = new PageInfo<>(userMapper.listUsers());
        return PageResult.fromPage(pageInfo, userConverter::toDtoList, pageRequest);
    }

    /**
     * 操作审计快照（不含密码），供 {@link org.chobit.knot.gateway.annotation.OperationLog} SpEL 使用。
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
        m.put("status", entity.getStatus());
        m.put("lastLoginTime", entity.getLastLoginTime());
        m.put("updatedAt", entity.getUpdatedAt());
        return m;
    }

    @Transactional
    public UserDto createUser(UserDto request) {
        UserEntity entity = new UserEntity();
        entity.setUsername(request.username());
        entity.setRealName(request.realName());
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

    @Transactional
    public UserDto updateUserStatus(Long id, String status) {
        UserEntity entity = userMapper.getUserById(id);
        if (entity == null) throw new BusinessException(ErrorCode.NOT_FOUND, "user not found");
        entity.setStatus(Integer.parseInt(status));
        userMapper.updateUserStatus(entity);
        return userConverter.toDto(entity);
    }

    @Transactional
    public UserDto updateUser(UserDto request) {
        UserEntity entity = userMapper.getUserById(request.id());
        if (entity == null) throw new BusinessException(ErrorCode.NOT_FOUND, "user not found");
        entity.setRealName(request.realName());
        entity.setStatus(request.status());
        // 如果提供了密码则更新
        if (request.password() != null && !request.password().isEmpty()) {
            entity.setPasswordHash(passwordEncoder.encode(request.password()));
        }
        userMapper.updateUser(entity);
        return userConverter.toDto(entity);
    }
}
