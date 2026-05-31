package org.chobit.knot.gateway.model;

import org.springframework.http.HttpStatus;

public record GatewayResponse(HttpStatus status, Object body) {
}
