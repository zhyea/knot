package org.chobit.knot.gateway.controller;

import org.chobit.knot.gateway.ApiResponse;
import org.chobit.knot.gateway.model.PageRequest;
import org.chobit.knot.gateway.model.PageResult;
import org.chobit.knot.gateway.converter.SecurityConverter;
import org.chobit.knot.gateway.dto.security.AlertItemDto;
import org.chobit.knot.gateway.dto.security.SecurityPolicyDto;
import org.chobit.knot.gateway.service.SecurityService;
import org.chobit.knot.gateway.vo.security.*;
import jakarta.validation.Valid;
import org.chobit.knot.gateway.dto.security.CacheEvictResultDto;
import org.chobit.knot.gateway.vo.security.*;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/security")
public class SecurityController {
    private final SecurityService securityService;
    private final SecurityConverter securityConverter;

    public SecurityController(SecurityService securityService, SecurityConverter securityConverter) {
        this.securityService = securityService;
        this.securityConverter = securityConverter;
    }

    @GetMapping("/overview")
    public ApiResponse<SecurityOverview> overview() {
        return ApiResponse.ok(securityConverter.toOverviewVO(securityService.overview()));
    }

    @PutMapping("/policies")
    public ApiResponse<SecurityPolicy> updatePolicy(@RequestBody @Valid SecurityPolicy request) {
        SecurityPolicyDto updated = securityService.updatePolicy(
                securityConverter.toPolicyDto(request)
        );
        return ApiResponse.ok(securityConverter.toPolicyVO(updated));
    }

    @GetMapping("/alerts")
    public ApiResponse<PageResult<AlertItem>> listAlerts(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        PageResult<AlertItemDto> page = securityService.listAlerts(PageRequest.of(pageNum, pageSize));
        return ApiResponse.ok(page.mapList(securityConverter::toAlertVOList));
    }

    @PostMapping("/cache/evict")
    public ApiResponse<CacheEvictResult> evictCache(@RequestBody @Valid CacheEvictRequest request) {
        CacheEvictResultDto r = securityService.evictCache(request.cacheKey(), request.cacheType());
        return ApiResponse.ok(securityConverter.toCacheEvictVO(r));
    }

}
