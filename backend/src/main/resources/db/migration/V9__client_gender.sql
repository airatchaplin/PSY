alter table client
    add column if not exists gender varchar(16);

comment on column client.gender is 'MALE | FEMALE — socialization cue for persona; null = legacy';
