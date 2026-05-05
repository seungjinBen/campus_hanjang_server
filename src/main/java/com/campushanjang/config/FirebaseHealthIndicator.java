package com.campushanjang.config;

import com.google.firebase.FirebaseApp;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("firebase")
public class FirebaseHealthIndicator implements HealthIndicator {

    @Override
    public Health health() {
        try {
            FirebaseApp.getInstance();
            return Health.up().build();
        } catch (Exception e) {
            return Health.down().withException(e).build();
        }
    }
}
