package com.psy.trainer.service;

import org.springframework.stereotype.Component;

@Component
public class PromptLoader {

    private final PromptStore promptStore;

    public PromptLoader(PromptStore promptStore) {
        this.promptStore = promptStore;
    }

    public String load(String classpathFile) {
        return promptStore.load(classpathFile);
    }
}
