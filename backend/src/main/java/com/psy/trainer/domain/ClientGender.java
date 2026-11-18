package com.psy.trainer.domain;

public enum ClientGender {
    MALE,
    FEMALE;

    public String labelRu() {
        return this == MALE ? "мужской" : "женский";
    }

    public String shortRu() {
        return this == MALE ? "М" : "Ж";
    }
}
