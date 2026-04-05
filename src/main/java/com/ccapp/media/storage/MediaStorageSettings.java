package com.ccapp.media.storage;

import java.net.URI;

public final class MediaStorageSettings {

    private final String endpoint;
    private final URI endpointUri;
    private final String publicEndpoint;
    private final URI presignerEndpointUri;
    private final String region;
    private final String accessKey;
    private final String secretKey;
    private final boolean pathStyle;

    public MediaStorageSettings(String endpoint,
                                URI endpointUri,
                                String publicEndpoint,
                                URI presignerEndpointUri,
                                String region,
                                String accessKey,
                                String secretKey,
                                boolean pathStyle) {
        this.endpoint = endpoint;
        this.endpointUri = endpointUri;
        this.publicEndpoint = publicEndpoint;
        this.presignerEndpointUri = presignerEndpointUri;
        this.region = region;
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        this.pathStyle = pathStyle;
    }

    public String endpoint() {
        return endpoint;
    }

    public URI endpointUri() {
        return endpointUri;
    }

    public String publicEndpoint() {
        return publicEndpoint;
    }

    public URI presignerEndpointUri() {
        return presignerEndpointUri;
    }

    public String region() {
        return region;
    }

    public String accessKey() {
        return accessKey;
    }

    public String secretKey() {
        return secretKey;
    }

    public boolean pathStyle() {
        return pathStyle;
    }
}
