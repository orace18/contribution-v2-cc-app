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
    public ResolvedContent resolve(NotificationChannel channel,
                                   String templateCode,
                                   String rawTitle,
                                   String rawMessage,
                                   Map<String, String> variables) {
        String title = renderNullable(rawTitle, variables);
        String message = rawMessage == null ? null : rawMessage.trim();
        if (message != null && !message.isBlank()) {
            return new ResolvedContent(templateCode, normalize(title), renderer.render(message, variables));
        }

        if (templateCode == null || templateCode.isBlank()) {
            throw new BusinessException("message or templateCode is required");
        }

        NotificationTemplate template = notificationTemplateRepository
                .findByCodeAndChannelAndActiveTrue(templateCode, channel)
                .orElseThrow(() -> new NotFoundException("Notification template not found"));

        String resolvedTitle = normalize(title);
        if (resolvedTitle == null && template.getSubject() != null && !template.getSubject().isBlank()) {
            resolvedTitle = renderer.render(template.getSubject(), variables);
        }
        return new ResolvedContent(
                template.getCode(),
                normalize(resolvedTitle),
                renderer.render(template.getBodyTemplate(), variables)
        );
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String renderNullable(String value, Map<String, String> variables) {
        String normalized = normalize(value);
        if (normalized == null) {
            return null;
        }
        return renderer.render(normalized, variables);
    }

    public record ResolvedContent(String templateCode, String title, String message) {
    }
}
