package org.chobit.knot.gateway.controller;

import jakarta.validation.Valid;
import org.chobit.knot.gateway.converter.TestRequestPresetConverter;
import org.chobit.knot.gateway.dto.routing.TestRequestPresetDto;
import org.chobit.knot.gateway.model.PageQuery;
import org.chobit.knot.gateway.model.PageRequest;
import org.chobit.knot.gateway.model.PageResult;
import org.chobit.knot.gateway.service.TestRequestPresetService;
import org.chobit.knot.gateway.vo.common.EnabledStatusRequest;
import org.chobit.knot.gateway.vo.routing.TestRequestPreset;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/test-request-presets")
public class TestRequestPresetController {
    private final TestRequestPresetService presetService;
    private final TestRequestPresetConverter presetConverter;

    /**
     * Constructs a new instance.
     */
    public TestRequestPresetController(TestRequestPresetService presetService, TestRequestPresetConverter presetConverter) {
        this.presetService = presetService;
        this.presetConverter = presetConverter;
    }

    @PostMapping("/list")
    public PageResult<TestRequestPreset> list(@RequestBody(required = false) PageQuery query) {
        PageResult<TestRequestPresetDto> page = presetService.list(
                query == null ? PageRequest.of(1, 20) : query.toPageRequest(),
                query == null ? null : query.keyword(),
                query == null ? null : query.protocol()
        );
        return page.mapList(presetConverter::toVOList);
    }

    @GetMapping("/options")
    public List<TestRequestPreset> options() {
        return presetConverter.toVOList(presetService.listActiveOptions());
    }

    @PostMapping
    public TestRequestPreset create(@RequestBody @Valid TestRequestPreset request) {
        TestRequestPresetDto created = presetService.create(presetConverter.toDto(request));
        return presetConverter.toVO(created);
    }

    @PutMapping("/{id}")
    public TestRequestPreset update(@PathVariable Long id, @RequestBody @Valid TestRequestPreset request) {
        TestRequestPresetDto updated = presetService.update(id, presetConverter.toDto(request));
        return presetConverter.toVO(updated);
    }

    @PutMapping("/{id}/status")
    public TestRequestPreset updateStatus(@PathVariable Long id, @RequestBody @Valid EnabledStatusRequest request) {
        TestRequestPresetDto updated = presetService.updateStatus(id, Boolean.TRUE.equals(request.enabled()));
        return presetConverter.toVO(updated);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        presetService.delete(id);
    }
}
