package org.chobit.knot.gateway.controller;

import jakarta.validation.Valid;
import org.chobit.knot.gateway.converter.ModelPoolConverter;
import org.chobit.knot.gateway.dto.model.ModelPoolDto;
import org.chobit.knot.gateway.model.PageQuery;
import org.chobit.knot.gateway.model.PageRequest;
import org.chobit.knot.gateway.model.PageResult;
import org.chobit.knot.gateway.service.ModelPoolService;
import org.chobit.knot.gateway.vo.model.ModelPool;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/model-pools")
public class ModelPoolController {

    private final ModelPoolService modelPoolService;
    private final ModelPoolConverter modelPoolConverter;

    public ModelPoolController(ModelPoolService modelPoolService, ModelPoolConverter modelPoolConverter) {
        this.modelPoolService = modelPoolService;
        this.modelPoolConverter = modelPoolConverter;
    }

    @GetMapping("/check-code")
    public Map<String, Boolean> checkCode(@RequestParam String code,
                                          @RequestParam(required = false) Long excludeId) {
        return Map.of("available", modelPoolService.isPoolCodeAvailable(code, excludeId));
    }

    @GetMapping("/{id}")
    public ModelPool get(@PathVariable Long id) {
        return modelPoolConverter.toVO(modelPoolService.getById(id));
    }

    @PostMapping("/list")
    public PageResult<ModelPool> list(@RequestBody(required = false) PageQuery query) {
        PageResult<ModelPoolDto> page = modelPoolService.list(
                query == null ? PageRequest.of(1, 20) : query.toPageRequest(),
                query == null ? null : query.keyword(),
                query == null ? null : query.modelTypes()
        );
        return page.mapList(modelPoolConverter::toVOList);
    }

    @PostMapping
    public ModelPool create(@RequestBody @Valid ModelPool request) {
        return modelPoolConverter.toVO(modelPoolService.create(modelPoolConverter.toDto(request)));
    }

    @PutMapping("/{id}")
    public ModelPool update(@PathVariable Long id, @RequestBody @Valid ModelPool request) {
        return modelPoolConverter.toVO(modelPoolService.update(id, modelPoolConverter.toDto(request)));
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        modelPoolService.delete(id);
    }
}
