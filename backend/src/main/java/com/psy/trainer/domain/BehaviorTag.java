package com.psy.trainer.domain;

import java.util.Arrays;
import java.util.List;

public enum BehaviorTag {
    ANXIOUS("тревожный"),
    AVOIDANT("избегающий"),
    RATIONALIZING("рационализирующий"),
    BOUNDARY_TESTING("тестирующий границы"),
    SHAME_PRONE("стыдящийся"),
    AFFECTIVE_CRYING("аффектный / плачущий"),
    DEVALUING("обесценивающий"),
    FUSED_DEPENDENT("слипшийся / зависимый"),
    INTELLECTUALIZING("интеллектуализирующий");

    private final String labelRu;

    BehaviorTag(String labelRu) {
        this.labelRu = labelRu;
    }

    public String getLabelRu() {
        return labelRu;
    }

    public static List<String> allCodes() {
        return Arrays.stream(values()).map(Enum::name).toList();
    }
}
