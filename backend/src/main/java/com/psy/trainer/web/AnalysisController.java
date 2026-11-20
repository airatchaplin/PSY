package com.psy.trainer.web;

import com.psy.trainer.service.AnalysisService;
import com.psy.trainer.web.dto.Dtos;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class AnalysisController {

    private final AnalysisService analysisService;

    public AnalysisController(AnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    @GetMapping("/sessions/{id}/analysis")
    public Dtos.AnalysisResponse getSession(@PathVariable UUID id) {
        return analysisService.getSessionAnalysis(id);
    }

    @PostMapping("/sessions/{id}/analysis")
    public Dtos.AnalysisResponse session(@PathVariable UUID id) {
        return analysisService.analyzeSession(id);
    }

    @PostMapping("/clients/{id}/analysis")
    public Dtos.AnalysisResponse client(@PathVariable UUID id) {
        return analysisService.analyzeClient(id);
    }

    @GetMapping("/analysis/global")
    public List<Dtos.AnalysisResponse> listGlobal() {
        return analysisService.listGlobalAnalyses();
    }

    @GetMapping("/analysis/global/{id}")
    public Dtos.AnalysisResponse getGlobal(@PathVariable UUID id) {
        return analysisService.getGlobalAnalysis(id);
    }

    @PostMapping("/analysis/global")
    public Dtos.AnalysisResponse global() {
        return analysisService.analyzeGlobal();
    }
}
