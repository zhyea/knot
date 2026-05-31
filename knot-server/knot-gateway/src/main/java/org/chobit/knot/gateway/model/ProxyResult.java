package org.chobit.knot.gateway.model;

public record ProxyResult(String responseBody,
                          Long providerId,
                          Long modelId,
                          String errorCode,
                          String errorMessage) {
}
