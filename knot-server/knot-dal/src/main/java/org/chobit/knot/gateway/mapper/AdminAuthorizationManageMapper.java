package org.chobit.knot.gateway.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.chobit.knot.gateway.entity.AdminApiPermissionBindingEntity;
import org.chobit.knot.gateway.entity.AdminMenuEntity;
import org.chobit.knot.gateway.entity.AdminModuleEntity;
import org.chobit.knot.gateway.entity.AdminPermissionEntity;
import org.chobit.knot.gateway.entity.AdminRoleEntity;

import java.util.List;

@Mapper
public interface AdminAuthorizationManageMapper {

    List<AdminRoleEntity> listRoles(@Param("keyword") String keyword);

    AdminRoleEntity getRoleById(@Param("id") Long id);

    Long countRoleByCode(@Param("code") String code, @Param("excludeId") Long excludeId);

    int insertRole(AdminRoleEntity entity);

    int updateRole(AdminRoleEntity entity);

    int deleteRole(@Param("id") Long id);

    Long countUserRoles(@Param("roleId") Long roleId);

    List<Long> listRolePermissionIds(@Param("roleId") Long roleId);

    int deleteRolePermissions(@Param("roleId") Long roleId);

    int insertRolePermissions(@Param("roleId") Long roleId, @Param("permissionIds") List<Long> permissionIds);

    List<AdminModuleEntity> listModules(@Param("keyword") String keyword);

    AdminModuleEntity getModuleById(@Param("id") Long id);

    Long countModuleByCode(@Param("moduleCode") String moduleCode, @Param("excludeId") Long excludeId);

    int insertModule(AdminModuleEntity entity);

    int updateModule(AdminModuleEntity entity);

    int updateModuleStatus(@Param("id") Long id, @Param("status") String status);

    int deleteModule(@Param("id") Long id);

    Long countMenusByModuleId(@Param("moduleId") Long moduleId);

    Long countPermissionsByModuleId(@Param("moduleId") Long moduleId);

    List<AdminMenuEntity> listMenus(@Param("keyword") String keyword,
                                    @Param("moduleId") Long moduleId,
                                    @Param("parentId") Long parentId);

    AdminMenuEntity getMenuById(@Param("id") Long id);

    Long countMenuByCode(@Param("menuCode") String menuCode, @Param("excludeId") Long excludeId);

    int insertMenu(AdminMenuEntity entity);

    int updateMenu(AdminMenuEntity entity);

    int updateMenuStatus(@Param("id") Long id, @Param("status") String status);

    int deleteMenu(@Param("id") Long id);

    Long countChildMenus(@Param("parentId") Long parentId);

    Long countPermissionsByMenuId(@Param("menuId") Long menuId);

    List<AdminPermissionEntity> listPermissions(@Param("keyword") String keyword,
                                                @Param("moduleId") Long moduleId,
                                                @Param("menuId") Long menuId,
                                                @Param("permissionType") String permissionType);

    AdminPermissionEntity getPermissionById(@Param("id") Long id);

    Long countPermissionByCode(@Param("permissionCode") String permissionCode, @Param("excludeId") Long excludeId);

    int insertPermission(AdminPermissionEntity entity);

    int updatePermission(AdminPermissionEntity entity);

    int updatePermissionStatus(@Param("id") Long id, @Param("status") String status);

    int deletePermission(@Param("id") Long id);

    Long countRolePermissionUsage(@Param("permissionId") Long permissionId);

    Long countApiBindingUsage(@Param("permissionId") Long permissionId);

    List<AdminApiPermissionBindingEntity> listApiPermissionBindings(@Param("keyword") String keyword,
                                                                    @Param("httpMethod") String httpMethod,
                                                                    @Param("permissionId") Long permissionId);

    AdminApiPermissionBindingEntity getApiPermissionBindingById(@Param("id") Long id);

    Long countApiPermissionBinding(@Param("httpMethod") String httpMethod,
                                   @Param("pathPattern") String pathPattern,
                                   @Param("excludeId") Long excludeId);

    int insertApiPermissionBinding(AdminApiPermissionBindingEntity entity);

    int updateApiPermissionBinding(AdminApiPermissionBindingEntity entity);

    int updateApiPermissionBindingStatus(@Param("id") Long id, @Param("status") String status);

    int deleteApiPermissionBinding(@Param("id") Long id);
}
