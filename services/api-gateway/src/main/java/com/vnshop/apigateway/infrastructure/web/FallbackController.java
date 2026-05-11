package com.vnshop.apigateway.infrastructure.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class FallbackController {

    @GetMapping("/fallback/{service}")
    ResponseEntity<ApiResponse<Map<String, Object>>> fallback(@PathVariable String service) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(ApiResponse.ok(Map.of(
                "error", "Service temporarily unavailable",
                "service", service,
                "status", HttpStatus.SERVICE_UNAVAILABLE.value()
            )));
    }
}
