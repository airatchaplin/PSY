package com.psy.trainer.web;

import com.psy.trainer.service.SupervisorService;
import com.psy.trainer.web.dto.Dtos;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/clients/{clientId}/supervisor")
public class SupervisorController {

    private final SupervisorService supervisorService;

    public SupervisorController(SupervisorService supervisorService) {
        this.supervisorService = supervisorService;
    }

    @GetMapping("/messages")
    public List<Dtos.MessageResponse> history(@PathVariable UUID clientId) {
        return supervisorService.history(clientId);
    }

    @PostMapping("/messages")
    public Dtos.MessageResponse ask(@PathVariable UUID clientId, @RequestBody Dtos.SendMessageRequest request) {
        return supervisorService.ask(clientId, request == null ? null : request.content());
    }
}
