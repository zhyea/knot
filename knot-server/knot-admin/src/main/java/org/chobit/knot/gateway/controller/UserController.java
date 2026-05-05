package org.chobit.knot.gateway.controller;

import jakarta.validation.Valid;
import org.chobit.knot.gateway.ApiResponse;
import org.chobit.knot.gateway.converter.UserConverter;
import org.chobit.knot.gateway.dto.user.UserDto;
import org.chobit.knot.gateway.model.PageQuery;
import org.chobit.knot.gateway.model.PageRequest;
import org.chobit.knot.gateway.model.PageResult;
import org.chobit.knot.gateway.service.UserService;
import org.chobit.knot.gateway.vo.user.UpdateUserStatusRequest;
import org.chobit.knot.gateway.vo.user.UserItem;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final UserService userService;
    private final UserConverter userConverter;

    public UserController(UserService userService, UserConverter userConverter) {
        this.userService = userService;
        this.userConverter = userConverter;
    }

    @PostMapping
    public ApiResponse<PageResult<UserItem>> list(@RequestBody(required = false) PageQuery query) {
        PageResult<UserDto> page = userService.listUsers(query == null ? PageRequest.of(1, 20) : query.toPageRequest());
        return ApiResponse.ok(page.mapList(userConverter::toVOList));
    }

    @PostMapping("/create")
    public ApiResponse<UserItem> create(@RequestBody @Valid UserItem request) {
        UserDto created = userService.createUser(new UserDto(
                null, request.username(), request.realName(), request.status()
        ));
        return ApiResponse.ok(userConverter.toVO(created));
    }

    @PutMapping("/{id}/status")
    public ApiResponse<UserItem> updateStatus(@PathVariable Long id, @RequestBody @Valid UpdateUserStatusRequest request) {
        UserDto updated = userService.updateUserStatus(id, request.status());
        return ApiResponse.ok(userConverter.toVO(updated));
    }
}
