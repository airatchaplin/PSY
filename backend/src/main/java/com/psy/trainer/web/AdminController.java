package com.psy.trainer.web;

import com.psy.trainer.service.AdminService;
import com.psy.trainer.web.dto.Dtos;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/overview")
    public Dtos.AdminOverviewResponse overview() {
        return adminService.overview();
    }

    @GetMapping("/models")
    public List<Dtos.AdminModelOption> models() {
        return adminService.models();
    }

    @PutMapping("/model")
    public Dtos.AdminOverviewResponse updateModel(@Valid @RequestBody Dtos.UpdateModelRequest request) {
        return adminService.updateModel(request);
    }

    @PutMapping("/connection")
    public Dtos.AdminOverviewResponse updateConnection(@RequestBody Dtos.UpdateLlmConnectionRequest request) {
        return adminService.updateConnection(request);
    }

    @GetMapping("/prompts")
    public List<Dtos.PromptSummary> prompts() {
        return adminService.prompts();
    }

    @GetMapping("/prompts/{id:.+}")
    public Dtos.PromptDetail prompt(@PathVariable String id) {
        return adminService.prompt(id);
    }

    @PutMapping("/prompts/{id:.+}")
    public Dtos.PromptDetail updatePrompt(
            @PathVariable String id,
            @RequestBody Dtos.UpdatePromptRequest request
    ) {
        return adminService.updatePrompt(id, request);
    }

    @GetMapping("/usage")
    public Dtos.AdminUsageStatsResponse usage(
            @RequestParam(required = false) Integer days,
            @RequestParam(required = false) java.time.Instant from,
            @RequestParam(required = false) java.time.Instant to,
            @RequestParam(required = false) String model,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) UUID therapistId
    ) {
        java.time.Instant safeTo = to != null ? to : java.time.Instant.now();
        java.time.Instant safeFrom = from;
        if (safeFrom == null) {
            int d = days == null ? 30 : Math.max(1, Math.min(days, 180));
            safeFrom = safeTo.minus(d, java.time.temporal.ChronoUnit.DAYS);
        }
        return adminService.usage(safeFrom, safeTo, model, source, therapistId);
    }

    @GetMapping("/users")
    public List<Dtos.AppUserResponse> users() {
        return adminService.listUsers();
    }

    @PostMapping("/users")
    public Dtos.AppUserResponse createUser(@RequestBody Dtos.CreateAppUserRequest request) {
        return adminService.createUser(request);
    }

    @PostMapping("/users/{id}/balance")
    public Dtos.AppUserResponse topUpUser(
            @PathVariable UUID id,
            @RequestBody Dtos.TopUpBalanceRequest request
    ) {
        return adminService.topUpUser(id, request);
    }

    @GetMapping("/balance/topups")
    public List<Dtos.BalanceTopUpResponse> topUpHistory() {
        return adminService.topUpHistory();
    }

    @GetMapping("/users/{id}/balance/history")
    public List<Dtos.BalanceTopUpResponse> userTopUpHistory(@PathVariable UUID id) {
        return adminService.userTopUpHistory(id);
    }
}
