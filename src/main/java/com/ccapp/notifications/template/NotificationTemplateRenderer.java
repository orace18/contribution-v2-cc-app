package com.ccapp.notifications.template;

import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class NotificationTemplateRenderer {

    public String render(String template, Map<String, String> variables) {
        if (template == null) {
            return null;
        }
        if (variables == null || variables.isEmpty()) {
            return template;
        }

        String rendered = template;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            String placeholder = "{{" + entry.getKey() + "}}";
            rendered = rendered.replace(placeholder, entry.getValue() == null ? "" : entry.getValue());
        }
        return rendered;
    }
}
