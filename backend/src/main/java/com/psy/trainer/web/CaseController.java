package com.psy.trainer.web;

import com.psy.trainer.service.CaseService;
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
@RequestMapping("/api/cases")
public class CaseController {

    private final CaseService caseService;

    public CaseController(CaseService caseService) {
        this.caseService = caseService;
    }

    @GetMapping
    public List<Dtos.CaseResponse> list() {
        return caseService.list();
    }

    @GetMapping("/peers")
    public List<Dtos.TherapistBrief> peers() {
        return caseService.listShareTargets();
    }

    @PostMapping
    public Dtos.CaseResponse create(@RequestBody Dtos.CreateCaseRequest request) {
        return caseService.createManual(request);
    }

    @PostMapping("/generate")
    public Dtos.CaseResponse generate(@RequestBody(required = false) Dtos.GenerateCaseRequest request) {
        return caseService.generate(request == null ? new Dtos.GenerateCaseRequest(null) : request);
    }

    @PostMapping("/{id}/share")
    public Dtos.ShareCaseResponse share(
            @PathVariable UUID id,
            @RequestBody(required = false) Dtos.ShareCaseRequest request
    ) {
        return caseService.share(id, request == null ? new Dtos.ShareCaseRequest(List.of(), false) : request);
    }

    @PostMapping("/{id}/share-link")
    public Dtos.CaseShareLinkResponse shareLink(@PathVariable UUID id) {
        return caseService.createShareLink(id);
    }

    @PostMapping("/shared/{token}/import")
    public Dtos.ImportSharedCaseResponse importShared(@PathVariable String token) {
        return caseService.importByShareToken(token);
    }
}
