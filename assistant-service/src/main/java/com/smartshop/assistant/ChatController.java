package com.smartshop.assistant;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/chat")
public class ChatController {

    private final ChatClient chatClient;

    public ChatController(ChatClient.Builder builder) {
        this.chatClient = builder
                .defaultSystem("""
                        You are SmartShop's customer support assistant.
                        Be friendly, short and clear.
                        If you do not know something, say so instead of guessing.
                        """)
                .build();
    }

    @PostMapping
    public ChatResponse chat(@RequestBody ChatRequest request) {
        requireMessage(request.message());

        String reply = chatClient.prompt()
                .user(request.message())
                .call()
                .content();

        return new ChatResponse(reply);
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> stream(@RequestParam String message) {
        requireMessage(message);

        return chatClient.prompt()
                .user(message)
                .stream()
                .content();
    }

    private void requireMessage(String message) {
        if (message == null || message.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "message must not be empty");
        }
    }

    public record ChatRequest(String message) {
    }

    public record ChatResponse(String reply) {
    }
}