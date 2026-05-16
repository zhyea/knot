package org.chobit.knot.gateway.controller;

import jakarta.validation.Valid;
import org.chobit.knot.gateway.annotation.OperationLog;
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
    public PageResult<UserItem> list(@RequestBody(required = false) PageQuery query) {
        PageResult<UserDto> page = userService.listUsers(query == null ? PageRequest.of(1, 20) : query.toPageRequest());
        return page.mapList(userConverter::toVOList);
    }

    @OperationLog(module = "user", operation = "CREATE", entityType = "User",
            entityIdAfter = "#result.id()",
            entityNameAfter = "#result.username()",
            description = "'创建用户'",
            recordNewValue = true,
            newValueSpel = "@userService.userAuditSnapshot(#result.id())")
    @PostMapping("/create")
    public UserItem create(@RequestBody @Valid UserItem request) {
        UserDto created = userService.createUser(new UserDto(
                null, request.username(), request.password(), request.realName(), request.status(), null, null
        ));
        return userConverter.toVO(created);
    }

    @OperationLog(module = "user", operation = "UPDATE", entityType = "User",
            entityId = "#p0",
            entityNameAfter = "#result.username()",
            description = "'更新用户状态'",
            recordOldValue = true,
            oldValueSpel = "@userService.userAuditSnapshot(#p0)",
            recordNewValue = true,
            newValueSpel = "@userService.userAuditSnapshot(#p0)")
    @PutMapping("/{id}/status")
    public UserItem updateStatus(@PathVariable Long id, @RequestBody @Valid UpdateUserStatusRequest request) {
        UserDto updated = userService.updateUserStatus(id, request.status());
        return userConverter.toVO(updated);
    }

    @OperationLog(module = "user", operation = "UPDATE", entityType = "User",
            entityId = "#p0",
            entityNameAfter = "#result.username()",
            description = "'更新用户'",
            recordOldValue = true,
            oldValueSpel = "@userService.userAuditSnapshot(#p0)",
            recordNewValue = true,
            newValueSpel = "@userService.userAuditSnapshot(#p0)")
    @PutMapping("/{id}")
    public UserItem updateUser(@PathVariable Long id, @RequestBody @Valid UserItem request) {
        UserDto updated = userService.updateUser(new UserDto(
                id, null, request.password() != null ? request.password() : null, request.realName(), request.status(), null, null
        ));
        return userConverter.toVO(updated);
    }
}
