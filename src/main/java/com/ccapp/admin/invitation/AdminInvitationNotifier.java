package com.ccapp.admin.invitation;

import com.ccapp.admin.domain.entity.AdminInvitation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class AdminInvitationNotifier {

    @Value("${admin.invitation.base-url:https://backoffice.platform.com/activate}")
    private String baseUrl;

    public void sendInvitation(AdminInvitation invitation) {
        String activationLink = baseUrl + "?token=" + invitation.getInvitationToken();
        log.info("Admin invitation email dispatched to {} role={} expiresAt={} activationLink={}",
                invitation.getEmail(), invitation.getRole(), invitation.getExpiresAt(), activationLink);
    }
}
