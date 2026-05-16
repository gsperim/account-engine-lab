package br.com.carrefour.consolidado.adapter.in.rest;

import org.springframework.boot.actuate.health.CompositeHealth;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/health")
public class HealthController {

    private final HealthEndpoint healthEndpoint;

    public HealthController(HealthEndpoint healthEndpoint) {
        this.healthEndpoint = healthEndpoint;
    }

    @GetMapping("/live")
    public ResponseEntity<Map<String, String>> liveness() {
        return ResponseEntity.ok(Map.of("status", "UP"));
    }

    @GetMapping("/ready")
    public ResponseEntity<Map<String, Object>> readiness() {
        var checks = new LinkedHashMap<String, String>();

        if (healthEndpoint.health() instanceof CompositeHealth composite) {
            var components = composite.getComponents();
            if (components.containsKey("db"))
                checks.put("database", components.get("db").getStatus().getCode());
            if (components.containsKey("rabbit"))
                checks.put("broker", components.get("rabbit").getStatus().getCode());
            if (components.containsKey("redis"))
                checks.put("cache", components.get("redis").getStatus().getCode());
        }

        boolean allUp = !checks.isEmpty() && checks.values().stream().allMatch("UP"::equals);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", allUp ? "UP" : "DOWN");
        body.put("checks", checks);

        return allUp ? ResponseEntity.ok(body) : ResponseEntity.status(503).body(body);
    }
}
