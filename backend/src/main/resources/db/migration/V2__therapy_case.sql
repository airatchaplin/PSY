CREATE TABLE therapy_case (
    id                   UUID PRIMARY KEY,
    slug                 VARCHAR(128) UNIQUE,
    title                VARCHAR(255) NOT NULL,
    presenting_request   TEXT NOT NULL,
    difficulty           VARCHAR(32) NOT NULL,
    suggested_tags       TEXT[] NOT NULL DEFAULT '{}',
    notes                TEXT,
    source               VARCHAR(32) NOT NULL DEFAULT 'BUILTIN',
    created_by           UUID REFERENCES therapist_profile(id),
    prompt_used          TEXT,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_therapy_case_created ON therapy_case(created_at DESC);

INSERT INTO therapy_case (id, slug, title, presenting_request, difficulty, suggested_tags, notes, source, created_at, updated_at)
VALUES
    ('a1000000-0000-4000-8000-000000000001', 'anxiety-work', 'Тревога на работе',
     'Постоянно тревожусь из-за оценок коллег', 'EASY',
     ARRAY['ANXIOUS', 'INTELLECTUALIZING'], 'Хороший старт для феноменологии', 'BUILTIN', NOW(), NOW()),
    ('a1000000-0000-4000-8000-000000000002', 'grief', 'Горе и потеря',
     'После расставания будто застрял(а) и ничего не чувствую', 'MEDIUM',
     ARRAY['AVOIDANT', 'AFFECTIVE_CRYING'], 'Работа с фигурой потери', 'BUILTIN', NOW(), NOW()),
    ('a1000000-0000-4000-8000-000000000003', 'boundaries', 'Границы в отношениях',
     'Не умею отказывать близким, потом злюсь', 'MEDIUM',
     ARRAY['FUSED_DEPENDENT', 'BOUNDARY_TESTING'], 'Конфлюэнция и границы', 'BUILTIN', NOW(), NOW()),
    ('a1000000-0000-4000-8000-000000000004', 'shame', 'Стыд и самоценность',
     'Стыдно просить о помощи, кажется что я слабый(ая)', 'HARD',
     ARRAY['SHAME_PRONE', 'RATIONALIZING'], 'Мягкий контакт со стыдом', 'BUILTIN', NOW(), NOW()),
    ('a1000000-0000-4000-8000-000000000005', 'devalue', 'Обесценивание терапии',
     'Не уверен(а), что разговоры вообще помогают', 'HARD',
     ARRAY['DEVALUING', 'RATIONALIZING'], 'Тестирование рамок и присутствия', 'BUILTIN', NOW(), NOW());
