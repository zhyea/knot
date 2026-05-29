package org.chobit.knot.gateway.dto.model;

public record ExternalModelSyncResult(
        int total,
        int inserted,
        int updated,
        int skipped,
        int failed,
        String message
) {
    public int affectedRows() {
        return inserted + updated;
    }
}
