package com.ccapp.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.env.Environment;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@Slf4j
@Configuration
public class FirebaseConfig {

    private static final List<String> CLASSPATH_FALLBACK_LOCATIONS = List.of(
            "firebase/firebase-service-account-prod.json",
            "firebase/firebase-service-account-dev.json",
            "firebase/jawupay-bbc9f-firebase-adminsdk-q6oiw-73173de43d.json"
    );

    public FirebaseConfig(@Value("${firebase.credentials.path}") String credentialsPath,
                          @Value("${firebase.project-id:}") String projectId,
                          Environment environment) {
        if (!FirebaseApp.getApps().isEmpty()) {
            return;
        }

        try {
            FirebaseOptions.Builder builder = FirebaseOptions.builder()
                    .setCredentials(loadCredentials(credentialsPath, environment.getActiveProfiles()));
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

    private GoogleCredentials loadCredentials(String credentialsPath, String[] activeProfiles) throws IOException {
        if (credentialsPath != null && !credentialsPath.isBlank()) {
            if (credentialsPath.startsWith("classpath:")) {
                return loadFromClasspath(credentialsPath.substring("classpath:".length()));
            }

            Path filePath = Path.of(credentialsPath);
            if (Files.isDirectory(filePath)) {
                log.warn("Firebase credentials path points to a directory ({}), falling back to classpath resource", filePath);
                return loadFromClasspath(resolveFallbackClasspathLocation(activeProfiles));
            }
            if (Files.exists(filePath)) {
                try (InputStream stream = new FileInputStream(credentialsPath)) {
                    return GoogleCredentials.fromStream(stream);
                }
            }
            log.warn("Firebase credentials file not found at {}, falling back to classpath resource", filePath);
            return loadFromClasspath(resolveFallbackClasspathLocation(activeProfiles));
        }

        return loadFromClasspath(resolveFallbackClasspathLocation(activeProfiles));
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

    private String resolveFallbackClasspathLocation(String[] activeProfiles) {
        if (activeProfiles != null) {
            for (String profile : activeProfiles) {
                String normalized = profile == null ? "" : profile.toLowerCase(Locale.ROOT);
                if ("prod".equals(normalized)) {
                    return "firebase/firebase-service-account-prod.json";
                }
                if ("dev".equals(normalized)) {
                    return "firebase/firebase-service-account-dev.json";
                }
            }
        }

        return CLASSPATH_FALLBACK_LOCATIONS.stream()
                .filter(this::classpathResourceExists)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No Firebase credentials found in classpath fallback locations for profiles "
                                + Arrays.toString(activeProfiles)
                ));
    }

    private boolean classpathResourceExists(String classpathLocation) {
        return new ClassPathResource(classpathLocation).exists();
    }
}
