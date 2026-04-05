package com.ccapp.media.storage;

import org.springframework.core.env.Environment;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Arrays;

public final class MediaStorageModeResolver {

    private MediaStorageModeResolver() {
    }

    public static MediaStorageModeDecision resolve(Environment environment) {
        int timeoutMs = resolveTimeout(environment.getProperty("media.local-dev.reachability-timeout-ms"));
        return resolve(
                environment.getActiveProfiles(),
                environment.getProperty("media.storage.endpoint"),
                timeoutMs
        );
    }

    static MediaStorageModeDecision resolve(String[] activeProfiles,
                                            String endpoint,
                                            int reachabilityTimeoutMs) {
        if (isDevProfile(activeProfiles)) {
            return new MediaStorageModeDecision(
                    MediaStorageMode.LOCAL_DEV,
                    "Spring profile 'dev' is active",
                    endpoint
            );
        }

        if (MediaStorageConfig.isBlank(endpoint)) {
            return new MediaStorageModeDecision(
                    MediaStorageMode.LOCAL_DEV,
                    "media.storage.endpoint is missing",
                    endpoint
            );
        }

        URI endpointUri;
        try {
            endpointUri = MediaStorageConfig.parseEndpoint(endpoint, "media.storage.endpoint");
        } catch (IllegalStateException ex) {
            return new MediaStorageModeDecision(
                    MediaStorageMode.LOCAL_DEV,
                    "media.storage.endpoint is invalid: " + ex.getMessage(),
                    endpoint
            );
        }

        if (!isHostResolvable(endpointUri.getHost())) {
            return new MediaStorageModeDecision(
                    MediaStorageMode.LOCAL_DEV,
                    "media.storage.endpoint host '" + endpointUri.getHost() + "' is not resolvable",
                    endpointUri.toString()
            );
        }

        if (!isEndpointReachable(endpointUri, reachabilityTimeoutMs)) {
            return new MediaStorageModeDecision(
                    MediaStorageMode.LOCAL_DEV,
                    "media.storage.endpoint '" + endpointUri + "' is not reachable from the backend runtime",
                    endpointUri.toString()
            );
        }

        return new MediaStorageModeDecision(
                MediaStorageMode.MINIO,
                "media.storage.endpoint is reachable",
                endpointUri.toString()
        );
    }

    private static int resolveTimeout(String rawTimeout) {
        if (MediaStorageConfig.isBlank(rawTimeout)) {
            return 1000;
        }
        try {
            return Math.max(100, Integer.parseInt(rawTimeout.trim()));
        } catch (NumberFormatException ex) {
            return 1000;
        }
    }

    private static boolean isDevProfile(String[] activeProfiles) {
        return Arrays.stream(activeProfiles)
                .filter(profile -> profile != null && !profile.isBlank())
                .anyMatch(profile -> "dev".equalsIgnoreCase(profile.trim()));
    }

    private static boolean isHostResolvable(String host) {
        try {
            InetAddress.getAllByName(host);
            return true;
        } catch (UnknownHostException ex) {
            return false;
        }
    }

    private static boolean isEndpointReachable(URI endpointUri, int timeoutMs) {
        int port = endpointUri.getPort() > 0 ? endpointUri.getPort() : defaultPort(endpointUri.getScheme());
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(endpointUri.getHost(), port), timeoutMs);
            return true;
        } catch (IOException ex) {
            return false;
        }
    }

    private static int defaultPort(String scheme) {
        return "https".equalsIgnoreCase(scheme) ? 443 : 80;
    }
}
