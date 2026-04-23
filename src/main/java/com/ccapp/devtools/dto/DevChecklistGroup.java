package com.ccapp.devtools.dto;

import java.util.List;

public record DevChecklistGroup(
        String group,
        List<DevChecklistEntry> entries
) {
}
