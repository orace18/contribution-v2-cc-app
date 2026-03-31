package com.ccapp.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.FileNotFoundException;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

@Slf4j
@Configuration
@ConditionalOnExpression("'${firebase.auth.enabled:false}' == 'true' || '${firebase.push.enabled:false}' == 'true'")
public class FirebaseConfig {

    @Bean
    public FirebaseApp firebaseApp(@Value("${firebase.credentials.path:}") String credentialsPath,
                                   @Value("${firebase.credentials.base64:}") String credentialsBase64,
                                   @Value("${firebase.project-id:}") String projectId) throws IOException {
        if (!FirebaseApp.getApps().isEmpty()) {
            return FirebaseApp.getInstance();
        }

        FirebaseOptions.Builder builder = FirebaseOptions.builder()
                .setCredentials(loadCredentials(credentialsPath, credentialsBase64));
        if (projectId != null && !projectId.isBlank()) {
            builder.setProjectId(projectId.trim());
        }

        FirebaseApp firebaseApp = FirebaseApp.initializeApp(builder.build());
        log.info("Firebase Admin SDK initialized projectId={}", projectId);
        return firebaseApp;
    }

    private GoogleCredentials loadCredentials(String credentialsPath, String credentialsBase64) throws IOException {
        if (credentialsBase64 != null && !credentialsBase64.isBlank()) {
            try (InputStream stream = new ByteArrayInputStream(Base64.getMimeDecoder().decode(credentialsBase64.trim()))) {
                return GoogleCredentials.fromStream(stream);
            } catch (IllegalArgumentException ex) {
                throw new IllegalStateException("Invalid Base64 value configured for firebase.credentials.base64", ex);
            }
        }

        if (credentialsPath != null && !credentialsPath.isBlank()) {
            if (credentialsPath.startsWith("classpath:")) {
                return loadFromClasspath(credentialsPath.substring("classpath:".length()));
            }

            Path filePath = Path.of(credentialsPath);
            if (Files.isDirectory(filePath)) {
                throw new IllegalStateException("Firebase credentials path points to a directory: " + filePath);
            }
            if (Files.exists(filePath)) {
                try (InputStream stream = new FileInputStream(credentialsPath)) {
                    return GoogleCredentials.fromStream(stream);
                }
            }
            throw new FileNotFoundException("Firebase credentials file not found: " + filePath);
        }

        throw new IllegalStateException(
                "Firebase authentication is enabled but no credentials were provided. "
                        + "Set firebase.credentials.path or firebase.credentials.base64."
        );
    }

    private GoogleCredentials loadFromClasspath(String classpathLocation) throws IOException {
        ClassPathResource resource = new ClassPathResource(classpathLocation);
        if (!resource.exists()) {
            throw new FileNotFoundException("Firebase credentials not found in classpath: " + classpathLocation);
        }
        try (InputStream stream = resource.getInputStream()) {
            return GoogleCredentials.fromStream(stream);
        }
    }
}
