package com.ccapp.devtools.dto;

public record DevChecklistEntry(
        String table,
        long residualCount,
        boolean clean,
        String note
) {
    public static DevChecklistEntry clean(String table) {
        return new DevChecklistEntry(table, 0L, true, null);
    }

    public static DevChecklistEntry residual(String table, long count) {
        return new DevChecklistEntry(table, count, false, null);
    }

    public static DevChecklistEntry withNote(String table, long count, String note) {
        return new DevChecklistEntry(table, count, count == 0, note);
    }
}
