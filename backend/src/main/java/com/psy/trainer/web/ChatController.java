package com.psy.trainer.web;

import com.psy.trainer.service.ChatService;
import com.psy.trainer.web.dto.Dtos;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
@RequestMapping("/api")
public class ChatController {

    private final ChatService chatService;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @GetMapping("/clients/{id}/messages")
    public List<Dtos.MessageResponse> history(@PathVariable UUID id) {
        return chatService.history(id);
    }

    @GetMapping("/sessions/{id}/messages")
    public List<Dtos.MessageResponse> sessionHistory(@PathVariable UUID id) {
        return chatService.sessionHistory(id);
    }

    @PostMapping("/clients/{id}/messages")
    public Dtos.MessageResponse sendToClient(
            @PathVariable UUID id,
            @RequestBody Dtos.SendMessageRequest request
    ) {
        return chatService.sendToClient(id, request == null ? null : request.content());
    }

    @PostMapping("/sessions/{id}/messages")
    public Dtos.MessageResponse send(@PathVariable UUID id, @RequestBody Dtos.SendMessageRequest request) {
        return chatService.send(id, request == null ? null : request.content());
    }

    @PostMapping(value = "/sessions/{id}/messages/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable UUID id, @RequestBody Dtos.SendMessageRequest request) {
        SseEmitter emitter = new SseEmitter(300_000L);
        executor.execute(() -> {
            try {
                Flux<String> flux = chatService.sendStream(id, request == null ? null : request.content());
                flux.toStream().forEach(token -> {
                    try {
                        emitter.send(SseEmitter.event().data(token));
                    } catch (IOException e) {
                        emitter.completeWithError(e);
                    }
                });
                emitter.complete();
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });
        return emitter;
    }
}
