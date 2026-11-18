package com.vozpublica.vozpublica.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class TempoConfig {

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}