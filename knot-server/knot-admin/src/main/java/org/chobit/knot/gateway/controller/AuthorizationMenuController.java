package org.chobit.knot.gateway.controller;

import org.chobit.knot.gateway.annotation.AuthCheck;
import org.chobit.knot.gateway.entity.AdminMenuEntity;
import org.chobit.knot.gateway.service.AuthorizationMenuService;
import org.chobit.knot.gateway.vo.common.EnabledStatusRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Menu endpoints for authorization management.
 */
@RestController
@RequestMapping("/api/system/authorizations/menus")
@AuthCheck
public class AuthorizationMenuController {

    private final AuthorizationMenuService menuService;

    /**
     * Constructs a new instance.
     */
    public AuthorizationMenuController(AuthorizationMenuService menuService) {
        this.menuService = menuService;
    }

    /**
     * Lists menus.
     */
    @GetMapping
    public List<AdminMenuEntity> listMenus(@RequestParam(required = false) String keyword,
                                           @RequestParam(required = false) Long moduleId,
                                           @RequestParam(required = false) Long parentId) {
        return menuService.listMenus(keyword, moduleId, parentId);
    }

    /**
     * Creates a menu.
     */
    @PostMapping
    public AdminMenuEntity createMenu(@RequestBody AdminMenuEntity request) {
        return menuService.createMenu(request);
    }

    /**
     * Updates a menu.
     */
    @PutMapping("/{id}")
    public AdminMenuEntity updateMenu(@PathVariable Long id, @RequestBody AdminMenuEntity request) {
        return menuService.updateMenu(id, request);
    }

    /**
     * Updates menu enabled status.
     */
    @PutMapping("/{id}/status")
    public AdminMenuEntity updateStatus(@PathVariable Long id, @RequestBody @Valid EnabledStatusRequest request) {
        return menuService.updateStatus(id, Boolean.TRUE.equals(request.enabled()));
    }

    /**
     * Deletes a menu.
     */
    @DeleteMapping("/{id}")
    public AdminMenuEntity deleteMenu(@PathVariable Long id) {
        return menuService.deleteMenu(id);
    }
}
