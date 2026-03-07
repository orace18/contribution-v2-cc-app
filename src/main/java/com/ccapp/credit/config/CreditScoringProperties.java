package com.ccapp.credit.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
@ConfigurationProperties(prefix = "credit.scoring")
public class CreditScoringProperties {

    private Map<String, BigDecimal> weights = defaultWeights();

    public Map<String, BigDecimal> getWeights() {
        return weights;
    }

    public void setWeights(Map<String, BigDecimal> weights) {
        this.weights = weights;
    }

    public BigDecimal weightFor(String key) {
        BigDecimal value = weights == null ? null : weights.get(key);
        return value == null ? BigDecimal.ZERO : value;
    }

    private static Map<String, BigDecimal> defaultWeights() {
        Map<String, BigDecimal> defaults = new LinkedHashMap<>();
        defaults.put("rms", new BigDecimal("0.60"));
        defaults.put("circle", new BigDecimal("0.40"));
        defaults.put("progress", BigDecimal.ZERO);
        defaults.put("training", BigDecimal.ZERO);
        defaults.put("activity", BigDecimal.ZERO);
        return defaults;
    }
}
