package io.docpilot.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HealthController {

    @Value("${spring.application.name:docpilot}")
    private String applicationName;

    @Value("${docpilot.version:1.0-SNAPSHOT}")
    private String version;

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of(
                "application", applicationName,
                "status", "UP",
                "version", version
        );
    }

}
