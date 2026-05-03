package com.knot.gateway.vo.security;

public record CacheEvictRequest(String cacheKey, String cacheType) {
}
