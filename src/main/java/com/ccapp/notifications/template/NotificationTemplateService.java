package com.ccapp.notifications.template;

import com.ccapp.common.exception.BusinessException;
import com.ccapp.common.exception.NotFoundException;
import com.ccapp.notifications.domain.entity.NotificationTemplate;
import com.ccapp.notifications.domain.enums.NotificationChannel;
import com.ccapp.notifications.repository.NotificationTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class NotificationTemplateService {

    private final NotificationTemplateRepository notificationTemplateRepository;
    private final NotificationTemplateRenderer renderer;

    @Transactional(readOnly = true)
    public ResolvedMessage resolve(NotificationChannel channel,
                                   String templateCode,
                                   String rawMessage,
                                   Map<String, String> variables) {
        String message = rawMessage == null ? null : rawMessage.trim();
        if (message != null && !message.isBlank()) {
            return new ResolvedMessage(templateCode, renderer.render(message, variables));
        }

        if (templateCode == null || templateCode.isBlank()) {
            throw new BusinessException("message or templateCode is required");
        }

        NotificationTemplate template = notificationTemplateRepository
                .findByCodeAndChannelAndActiveTrue(templateCode, channel)
                .orElseThrow(() -> new NotFoundException("Notification template not found"));

        return new ResolvedMessage(template.getCode(), renderer.render(template.getBodyTemplate(), variables));
    }

    public record ResolvedMessage(String templateCode, String message) {
    }
}
