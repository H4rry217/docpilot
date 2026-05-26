package io.docpilot.controller;

import io.docpilot.common.result.Result;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/health")
public class HealthController {

    @Value("${spring.application.name:docpilot}")
    private String applicationName;

    @Value("${docpilot.version:1.0-SNAPSHOT}")
    private String version;

    @GetMapping
    public Result<Map<String, String>> health() {
        return Result.success(Map.of(
                "application", applicationName,
                "status", "UP",
                "version", version
        ));
    }

}
