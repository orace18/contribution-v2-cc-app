package com.ccapp.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class WellKnownController {

    @Value("${app.deep-link.apple-team-id}")
    private String appleTeamId;

    @Value("${app.deep-link.apple-bundle-id}")
    private String appleBundleId;

    @Value("${app.deep-link.android-package}")
    private String androidPackage;

    @Value("${app.deep-link.android-sha256-fingerprint}")
    private String androidSha256Fingerprint;

    @GetMapping(value = "/.well-known/apple-app-site-association", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> appleAppSiteAssociation() {
        String json = """
                {
                  "applinks": {
                    "apps": [],
                    "details": [
                      {
                        "appID": "%s.%s",
                        "paths": ["/join*"]
                      }
                    ]
                  }
                }
                """.formatted(appleTeamId, appleBundleId);
        return ResponseEntity.ok(json);
    }

    @GetMapping(value = "/.well-known/assetlinks.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> assetLinks() {
        String json = """
                [
                  {
                    "relation": ["delegate_permission/common.handle_all_urls"],
                    "target": {
                      "namespace": "android_app",
                      "package_name": "%s",
                      "sha256_cert_fingerprints": ["%s"]
                    }
                  }
                ]
                """.formatted(androidPackage, androidSha256Fingerprint);
        return ResponseEntity.ok(json);
    }
}
