package com.ccapp.admin.invitation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class AdminInvitationExpiryJob {

    private final AdminInvitationService adminInvitationService;

    @Scheduled(fixedDelayString = "${admin.invitation.expire-job-delay-ms:900000}")
    public void expireInvitations() {
        int count = adminInvitationService.expireOutdatedInvitations();
        if (count > 0) {
            log.info("Expired {} outdated admin invitation(s)", count);
        }
    }
}
