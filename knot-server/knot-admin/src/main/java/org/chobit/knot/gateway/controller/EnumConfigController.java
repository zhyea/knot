package org.chobit.knot.gateway.controller;

import org.chobit.knot.gateway.ApiResponse;
import org.chobit.knot.gateway.entity.EnumConfigEntity;
import org.chobit.knot.gateway.model.PageQuery;
import org.chobit.knot.gateway.model.PageRequest;
import org.chobit.knot.gateway.model.PageResult;
import org.chobit.knot.gateway.service.EnumConfigService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/system/enums")
public class EnumConfigController {

    private final EnumConfigService enumConfigService;

    public EnumConfigController(EnumConfigService enumConfigService) {
        this.enumConfigService = enumConfigService;
    }

    /** 分页查询所有枚举项 */
    @PostMapping("/list")
    public ApiResponse<PageResult<EnumConfigEntity>> list(@RequestBody(required = false) PageQuery query) {
        PageRequest pr = query == null ? PageRequest.of(1, 20) : query.toPageRequest();
        return ApiResponse.ok(enumConfigService.list(pr, query != null ? query.category() : null));
    }

    /** 按分类查询枚举项（前端下拉框用） */
    @PostMapping("/{category}")
    public ApiResponse<List<EnumConfigEntity>> listByCategory(@PathVariable String category) {
        return ApiResponse.ok(enumConfigService.listByCategory(category));
    }

    /** 查询所有分类 */
    @PostMapping("/categories")
    public ApiResponse<List<String>> listCategories() {
        return ApiResponse.ok(enumConfigService.listCategories());
    }

    /** 新增枚举项 */
    @PostMapping
    public ApiResponse<EnumConfigEntity> create(@RequestBody EnumConfigEntity request) {
        return ApiResponse.ok(enumConfigService.create(request));
    }

    /** 修改枚举项（仅 label/sort/enabled/remark） */
    @PutMapping("/{id}")
    public ApiResponse<EnumConfigEntity> update(@PathVariable Long id, @RequestBody EnumConfigEntity request) {
        return ApiResponse.ok(enumConfigService.update(id, request));
    }

    /** 删除枚举项（系统内置不可删） */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        enumConfigService.delete(id);
        return ApiResponse.ok(null);
    }
}
