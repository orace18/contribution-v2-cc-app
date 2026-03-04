package com.ccapp.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

@Slf4j
@Configuration
@ConditionalOnProperty(name = "firebase.auth.enabled", havingValue = "true")
public class FirebaseConfig {

    public FirebaseConfig(@Value("${firebase.credentials.path:}") String credentialsPath,
                          @Value("${firebase.project-id:}") String projectId) {
        if (!FirebaseApp.getApps().isEmpty()) {
            return;
        }

        try {
            FirebaseOptions.Builder builder = FirebaseOptions.builder()
                    .setCredentials(loadCredentials(credentialsPath));
            if (projectId != null && !projectId.isBlank()) {
                builder.setProjectId(projectId);
            }
            FirebaseOptions options = builder.build();
            FirebaseApp.initializeApp(options);
            log.info("Firebase Admin SDK initialized");
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to initialize Firebase Admin SDK", ex);
        }
    }

    private GoogleCredentials loadCredentials(String credentialsPath) throws IOException {
        if (credentialsPath != null && !credentialsPath.isBlank()) {
            try (InputStream stream = new FileInputStream(credentialsPath)) {
                return GoogleCredentials.fromStream(stream);
            }
        }
        return GoogleCredentials.getApplicationDefault();
    }
}
