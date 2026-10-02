package org.chobit.knot.gateway.adapter.auth;

import java.util.List;

public record UpstreamAuthApplierDefinition(String code,
                                            String label,
                                            List<String> credentialTypes,
                                            String className) {
}
