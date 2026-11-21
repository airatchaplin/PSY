package com.psy.trainer.web;

import com.psy.trainer.service.MeService;
import com.psy.trainer.web.dto.Dtos;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/me")
public class MeController {

    private final MeService meService;

    public MeController(MeService meService) {
        this.meService = meService;
    }

    @GetMapping
    public Dtos.MeResponse me() {
        return meService.me();
    }

    @PostMapping("/accept-disclaimer")
    public Dtos.MeResponse acceptDisclaimer() {
        return meService.acceptDisclaimer();
    }

    @PutMapping("/voice-settings")
    public Dtos.MeResponse updateVoiceSettings(@RequestBody Dtos.UpdateVoiceSettingsRequest request) {
        return meService.updateVoiceSettings(request);
    }

    @GetMapping("/balance/history")
    public List<Dtos.BalanceTopUpResponse> balanceHistory() {
        return meService.balanceHistory();
    }

    @GetMapping("/usage")
    public Dtos.AdminUsageStatsResponse usage(
            @RequestParam(required = false) Integer days,
            @RequestParam(required = false) java.time.Instant from,
            @RequestParam(required = false) java.time.Instant to,
            @RequestParam(required = false) String model,
            @RequestParam(required = false) String source
    ) {
        java.time.Instant safeTo = to != null ? to : java.time.Instant.now();
        java.time.Instant safeFrom = from;
        if (safeFrom == null) {
            int d = days == null ? 30 : Math.max(1, Math.min(days, 180));
            safeFrom = safeTo.minus(d, java.time.temporal.ChronoUnit.DAYS);
        }
        return meService.usage(safeFrom, safeTo, model, source);
    }
}
