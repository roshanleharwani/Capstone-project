package com.nexusgateway.app.controllers;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/settings")
public class SettingsController {

    @GetMapping
    public SettingsPayload getSettings() {
        return new SettingsPayload(
            Boolean.parseBoolean(System.getProperty("nexus.settings.regexScanner", "true")),
            Boolean.parseBoolean(System.getProperty("nexus.settings.secretScanner", "true")),
            Boolean.parseBoolean(System.getProperty("nexus.settings.nerScanner", "true")),
            Boolean.parseBoolean(System.getProperty("nexus.settings.cascadeGuard", "true")),
            Boolean.parseBoolean(System.getProperty("nexus.settings.stackelberg", "true")),
            Boolean.parseBoolean(System.getProperty("nexus.settings.cacheEnabled", "true"))
        );
    }

    @PostMapping
    public SettingsPayload updateSettings(@RequestBody SettingsPayload payload) {
        System.setProperty("nexus.settings.regexScanner", String.valueOf(payload.regexScanner()));
        System.setProperty("nexus.settings.secretScanner", String.valueOf(payload.secretScanner()));
        System.setProperty("nexus.settings.nerScanner", String.valueOf(payload.nerScanner()));
        System.setProperty("nexus.settings.cascadeGuard", String.valueOf(payload.cascadeGuard()));
        System.setProperty("nexus.settings.stackelberg", String.valueOf(payload.stackelberg()));
        System.setProperty("nexus.settings.cacheEnabled", String.valueOf(payload.cacheEnabled()));
        return payload;
    }

    public record SettingsPayload(
        boolean regexScanner,
        boolean secretScanner,
        boolean nerScanner,
        boolean cascadeGuard,
        boolean stackelberg,
        boolean cacheEnabled
    ) {}
}
