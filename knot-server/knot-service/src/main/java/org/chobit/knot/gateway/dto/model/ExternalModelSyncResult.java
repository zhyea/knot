package org.chobit.knot.gateway.dto.model;

public record ExternalModelSyncResult(
        int total,
        int inserted,
        int updated,
        int skipped,
        int failed,
        String message
) {
    /**
     * Executes the public operation. Executes the public operation.
     */
    public int affectedRows() {
        return inserted + updated;
    }
}
