package com.psy.trainer.domain;

public enum Difficulty {
    EASY("лёгкий"),
    MEDIUM("средний"),
    HARD("сложный");

    private final String labelRu;

    Difficulty(String labelRu) {
        this.labelRu = labelRu;
    }

    public String getLabelRu() {
        return labelRu;
    }
}
