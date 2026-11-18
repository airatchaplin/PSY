package com.psy.trainer.web;

import com.psy.trainer.service.ClientQueryService;
import com.psy.trainer.service.ExportService;
import com.psy.trainer.service.NoteService;
import com.psy.trainer.service.ReceptionService;
import com.psy.trainer.web.dto.Dtos;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class ClientController {

    private final ClientQueryService clientQueryService;
    private final ReceptionService receptionService;
    private final NoteService noteService;
    private final ExportService exportService;

    public ClientController(
            ClientQueryService clientQueryService,
            ReceptionService receptionService,
            NoteService noteService,
            ExportService exportService
    ) {
        this.clientQueryService = clientQueryService;
        this.receptionService = receptionService;
        this.noteService = noteService;
        this.exportService = exportService;
    }

    @GetMapping("/clients")
    public List<Dtos.ClientResponse> clients() {
        return clientQueryService.list();
    }

    @GetMapping("/clients/{id}")
    public Dtos.ClientResponse client(@PathVariable UUID id) {
        return clientQueryService.get(id);
    }

    @GetMapping("/clients/{id}/sessions")
    public List<Dtos.SessionSummaryResponse> sessions(@PathVariable UUID id) {
        return clientQueryService.sessions(id);
    }

    @PostMapping("/receptions/start")
    public Dtos.ReceptionResponse start(@RequestBody(required = false) Dtos.StartReceptionRequest request) {
        return receptionService.startNew(request);
    }

    @PostMapping("/clients/{id}/sessions/start")
    public Dtos.SessionResponse continueSession(@PathVariable UUID id) {
        return receptionService.continueClient(id);
    }

    @PostMapping("/sessions/{id}/end")
    public Dtos.SessionResponse end(
            @PathVariable UUID id,
            @RequestBody(required = false) Dtos.TimerUpdateRequest body
    ) {
        Integer elapsed = body == null ? null : body.elapsedSeconds();
        return receptionService.endSession(id, elapsed);
    }

    @PostMapping("/sessions/{id}/timer")
    public Dtos.SessionResponse timer(
            @PathVariable UUID id,
            @RequestBody Dtos.TimerUpdateRequest body
    ) {
        return receptionService.updateTimer(id, body);
    }

    @GetMapping("/clients/{id}/notes")
    public List<Dtos.NoteResponse> notes(@PathVariable UUID id) {
        return noteService.list(id);
    }

    @PostMapping("/clients/{id}/notes")
    public Dtos.NoteResponse createNote(@PathVariable UUID id, @RequestBody Dtos.NoteRequest request) {
        return noteService.create(id, request);
    }

    @GetMapping("/clients/{id}/export")
    public Dtos.ExportResponse export(@PathVariable UUID id) {
        return exportService.exportClient(id);
    }
}
