package com.ccapp.shop.controller;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LegacyShopMutableEndpointInventoryTest {

    @Test
    void legacyMutableEndpointInventoryShouldMatchExpectedSet() throws Exception {
        Set<String> actual = scanMutableLegacyShopEndpoints();
        Set<String> expected = Set.of(
                "PATCH /shop/orders/{orderId}/status",
                "POST /shop/orders/checkout",
                "POST /shop/orders/{orderId}/refund",
                "POST /shop/products"
        );

        assertEquals(new TreeSet<>(expected), new TreeSet<>(actual));
    }

    private Set<String> scanMutableLegacyShopEndpoints() throws Exception {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));

        Set<String> endpoints = new TreeSet<>();
        for (var component : scanner.findCandidateComponents("com.ccapp.shop.controller")) {
            Class<?> controllerClass = Class.forName(component.getBeanClassName());
            String basePath = normalize(singlePath(AnnotatedElementUtils.findMergedAnnotation(controllerClass, RequestMapping.class)));
            for (Method method : controllerClass.getDeclaredMethods()) {
                addMapping(endpoints, "POST", basePath, singlePath(AnnotatedElementUtils.findMergedAnnotation(method, PostMapping.class)));
                addMapping(endpoints, "PATCH", basePath, singlePath(AnnotatedElementUtils.findMergedAnnotation(method, PatchMapping.class)));
                addMapping(endpoints, "PUT", basePath, singlePath(AnnotatedElementUtils.findMergedAnnotation(method, PutMapping.class)));
                addMapping(endpoints, "DELETE", basePath, singlePath(AnnotatedElementUtils.findMergedAnnotation(method, DeleteMapping.class)));
            }
        }
        return endpoints;
    }

    private void addMapping(Set<String> endpoints, String httpMethod, String basePath, String methodPath) {
        if (methodPath == null) {
            return;
        }
        endpoints.add(httpMethod + " " + join(basePath, methodPath));
    }

    private String singlePath(RequestMapping mapping) {
        if (mapping == null) {
            return "/";
        }
        if (mapping.path().length > 0) {
            return mapping.path()[0];
        }
        if (mapping.value().length > 0) {
            return mapping.value()[0];
        }
        return "/";
    }

    private String singlePath(PostMapping mapping) {
        return mapping == null ? null : firstNonEmpty(mapping.path(), mapping.value());
    }

    private String singlePath(PatchMapping mapping) {
        return mapping == null ? null : firstNonEmpty(mapping.path(), mapping.value());
    }

    private String singlePath(PutMapping mapping) {
        return mapping == null ? null : firstNonEmpty(mapping.path(), mapping.value());
    }

    private String singlePath(DeleteMapping mapping) {
        return mapping == null ? null : firstNonEmpty(mapping.path(), mapping.value());
    }

    private String firstNonEmpty(String[] primary, String[] fallback) {
        if (primary != null && primary.length > 0) {
            return primary[0];
        }
        if (fallback != null && fallback.length > 0) {
            return fallback[0];
        }
        return "";
    }

    private String join(String basePath, String methodPath) {
        String normalizedBase = normalize(basePath);
        String normalizedMethod = normalize(methodPath);
        if ("/".equals(normalizedMethod)) {
            return normalizedBase;
        }
        if ("/".equals(normalizedBase)) {
            return normalizedMethod;
        }
        return normalize(normalizedBase + "/" + normalizedMethod);
    }

    private String normalize(String path) {
        if (path == null || path.isBlank()) {
            return "/";
        }
        String normalized = path.startsWith("/") ? path : "/" + path;
        normalized = normalized.replaceAll("/+", "/");
        if (normalized.length() > 1 && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
