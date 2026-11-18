package com.psy.trainer.service;

import com.psy.trainer.domain.TherapistNote;
import com.psy.trainer.domain.TherapistProfile;
import com.psy.trainer.repo.ClientRepository;
import com.psy.trainer.repo.TherapistNoteRepository;
import com.psy.trainer.web.dto.Dtos;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class NoteService {

    private final CurrentTherapistService currentTherapistService;
    private final ClientRepository clientRepository;
    private final TherapistNoteRepository noteRepository;
    private final ClientMapper mapper;

    public NoteService(
            CurrentTherapistService currentTherapistService,
            ClientRepository clientRepository,
            TherapistNoteRepository noteRepository,
            ClientMapper mapper
    ) {
        this.currentTherapistService = currentTherapistService;
        this.clientRepository = clientRepository;
        this.noteRepository = noteRepository;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<Dtos.NoteResponse> list(UUID clientId) {
        TherapistProfile therapist = currentTherapistService.requireCurrent();
        ensureClient(clientId, therapist.getId());
        return noteRepository.findByClientIdAndTherapistIdOrderByCreatedAtDesc(clientId, therapist.getId())
                .stream()
                .map(mapper::toNote)
                .toList();
    }

    @Transactional
    public Dtos.NoteResponse create(UUID clientId, Dtos.NoteRequest request) {
        TherapistProfile therapist = currentTherapistService.requireCurrent();
        ensureClient(clientId, therapist.getId());
        if (request == null || request.content() == null || request.content().isBlank()) {
            throw new BadRequestException("Пустая заметка");
        }
        TherapistNote note = noteRepository.save(TherapistNote.create(
                therapist.getId(),
                clientId,
                request.sessionId(),
                request.content().trim()
        ));
        return mapper.toNote(note);
    }

    private void ensureClient(UUID clientId, UUID therapistId) {
        clientRepository.findByIdAndTherapistId(clientId, therapistId)
                .orElseThrow(() -> new NotFoundException("Клиент не найден"));
    }
}
