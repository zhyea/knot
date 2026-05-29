package org.chobit.knot.gateway.vo.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ModelPool(
        Long id,
        @NotBlank @Size(max = 64) String poolCode,
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(max = 64) String modelType,
        @NotBlank @Size(max = 32) String selectionStrategy,
        boolean enabled,
        @Size(max = 255) String remark,
        @Valid List<ModelPoolItem> items
) {
}
