package com.psy.trainer.service;

import com.psy.trainer.domain.BehaviorTag;
import com.psy.trainer.domain.Difficulty;
import com.psy.trainer.web.dto.Dtos;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
public class LabelMapper {

    public String difficultyRu(Difficulty difficulty) {
        return difficulty == null ? "" : difficulty.getLabelRu();
    }

    public String tagRu(String code) {
        if (code == null || code.isBlank()) {
            return "";
        }
        try {
            return BehaviorTag.valueOf(code.trim().toUpperCase()).getLabelRu();
        } catch (Exception e) {
            return code;
        }
    }

    public Dtos.TagDto toTag(String code) {
        return new Dtos.TagDto(code, tagRu(code));
    }

    public List<Dtos.TagDto> toTags(String[] codes) {
        if (codes == null) {
            return List.of();
        }
        return Arrays.stream(codes).map(this::toTag).toList();
    }

    public List<Dtos.TagDto> toTags(List<String> codes) {
        if (codes == null) {
            return List.of();
        }
        return codes.stream().map(this::toTag).toList();
    }
}
