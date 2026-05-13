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

import java.util.List;

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
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户名或密码错误");
        }
        String token = JwtUtil.generateToken(user.getId(), user.getUsername());
        return new LoginResponse(token, user.getId(), user.getUsername(), user.getRealName());
    }

    public PageResult<UserDto> listUsers(PageRequest pageRequest) {
        PageHelper.startPage(pageRequest.pageNum(), pageRequest.pageSize());
        PageInfo<UserEntity> pageInfo = new PageInfo<>(userMapper.listUsers());
        return PageResult.fromPage(pageInfo, userConverter::toDtoList, pageRequest);
    }

    @Transactional
    public UserDto createUser(UserDto request) {
        UserEntity entity = new UserEntity();
        entity.setUsername(request.username());
        entity.setRealName(request.realName());
        entity.setStatus(request.status());
        userMapper.insertUser(entity);
        return userConverter.toDto(entity);
    }

    @Transactional
    public UserDto updateUserStatus(Long id, String status) {
        UserEntity entity = userMapper.getUserById(id);
        if (entity == null) throw new BusinessException(ErrorCode.NOT_FOUND, "user not found");
        entity.setStatus(status);
        userMapper.updateUserStatus(entity);
        return userConverter.toDto(entity);
    }
}
