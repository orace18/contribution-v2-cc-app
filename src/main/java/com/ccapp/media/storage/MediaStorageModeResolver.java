package com.ccapp.media.storage;

import org.springframework.core.env.Environment;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.UnknownHostException;

public final class MediaStorageModeResolver {

    private MediaStorageModeResolver() {
    }

    public static MediaStorageModeDecision resolve(Environment environment) {
        int timeoutMs = resolveTimeout(
                firstNonBlank(
                        environment.getProperty("media.storage.reachability-timeout-ms"),
                        environment.getProperty("media.local-file.reachability-timeout-ms"),
                        environment.getProperty("media.local-dev.reachability-timeout-ms")
                )
        );
        boolean strictStartup = resolveStrictStartup(environment.getProperty("media.storage.startup-strict"));
        return resolve(
                environment.getProperty("media.storage.endpoint"),
                timeoutMs,
                strictStartup
        );
    }

    static MediaStorageModeDecision resolve(String endpoint,
                                            int reachabilityTimeoutMs) {
        return resolve(endpoint, reachabilityTimeoutMs, false);
    }

    static MediaStorageModeDecision resolve(String endpoint,
                                            int reachabilityTimeoutMs,
                                            boolean strictStartup) {
        if (MediaStorageConfig.isBlank(endpoint)) {
            return fallbackDecision(
                    strictStartup,
                    "media.storage.endpoint is missing",
                    endpoint
            );
        }

        URI endpointUri;
        try {
            endpointUri = MediaStorageConfig.parseEndpoint(endpoint, "media.storage.endpoint");
        } catch (IllegalStateException ex) {
            return fallbackDecision(
                    strictStartup,
                    "media.storage.endpoint is invalid: " + ex.getMessage(),
                    endpoint
            );
        }

        if (!isHostResolvable(endpointUri.getHost())) {
            return fallbackDecision(
                    strictStartup,
                    "media.storage.endpoint host '" + endpointUri.getHost() + "' is not resolvable",
                    endpointUri.toString()
            );
        }

        if (!isEndpointReachable(endpointUri, reachabilityTimeoutMs)) {
            return fallbackDecision(
                    strictStartup,
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

    private static boolean resolveStrictStartup(String rawStrictStartup) {
        if (MediaStorageConfig.isBlank(rawStrictStartup)) {
            return false;
        }
        return Boolean.parseBoolean(rawStrictStartup.trim());
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (!MediaStorageConfig.isBlank(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private static MediaStorageModeDecision fallbackDecision(boolean strictStartup,
                                                             String reason,
                                                             String endpoint) {
        if (strictStartup) {
            throw new IllegalStateException(
                    "Media storage strict startup is enabled and fallback is disabled: " + reason
            );
        }
        return new MediaStorageModeDecision(
                MediaStorageMode.LOCAL_FILE,
                reason,
                endpoint
        );
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
