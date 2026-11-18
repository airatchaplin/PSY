package com.psy.trainer.web;

import com.psy.trainer.service.MeService;
import com.psy.trainer.web.dto.Dtos;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
