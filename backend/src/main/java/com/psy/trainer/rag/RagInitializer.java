package com.psy.trainer.rag;

import com.psy.trainer.service.CaseService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class RagInitializer implements ApplicationRunner {

    private final RagService ragService;
    private final CaseService caseService;

    public RagInitializer(RagService ragService, CaseService caseService) {
        this.ragService = ragService;
        this.caseService = caseService;
    }

    @Override
    public void run(ApplicationArguments args) {
        ragService.init();
        caseService.reindexAllIntoRag();
    }
}
