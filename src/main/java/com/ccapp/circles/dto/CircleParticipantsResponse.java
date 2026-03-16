package com.ccapp.circles.dto;

import java.util.List;

public record CircleParticipantsResponse(
        List<CircleMemberResponse> members,
        CircleMemberResponse coach,
        List<CircleMemberResponse> advisors
) {
}
