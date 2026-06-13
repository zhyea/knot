package org.chobit.knot.gateway.model;

public record ProxyResult(String responseBody,
                          Long providerId,
                          Long modelId,
                          NormalizedUsage usage) {

}
