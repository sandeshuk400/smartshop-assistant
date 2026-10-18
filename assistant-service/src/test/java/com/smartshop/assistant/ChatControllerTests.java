package com.smartshop.assistant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.vectorstore.VectorStore;

import jakarta.servlet.http.HttpServletRequest;

class ChatControllerTests {

    private ChatClient chatClient;
    private ChatController controller;
    private HttpServletRequest http;

    @BeforeEach
    void setUp() {
        chatClient = mock(ChatClient.class);
        ChatClient.Builder builder = mock(ChatClient.Builder.class, Answers.RETURNS_SELF);
        when(builder.build()).thenReturn(chatClient);

        VectorStore vectorStore = mock(VectorStore.class);
        OrderRepository orders = mock(OrderRepository.class);
        controller = new ChatController(builder, vectorStore, orders);

        http = mock(HttpServletRequest.class);
        when(http.getRemoteAddr()).thenReturn("127.0.0.1");
    }

    private ChatController.ChatResponse ask(String message) {
        return controller.chat(new ChatController.ChatRequest(message, "test-session"), http);
    }

    @Test
    void blankMessageIsRejected() {
        assertThrows(RuntimeException.class, () -> ask("   "));
    }

    @Test
    void shippingQuestionGetsAReplyWithoutCallingTheModel() {
        ChatController.ChatResponse response = ask("What are your shipping charges?");
        assertNotNull(response.reply());
        assertFalse(response.reply().isBlank());
        verifyNoInteractions(chatClient);
    }

    @Test
    void returnQuestionGetsAReplyWithoutCallingTheModel() {
        ChatController.ChatResponse response = ask("What is your return policy?");
        assertNotNull(response.reply());
        assertFalse(response.reply().isBlank());
        verifyNoInteractions(chatClient);
    }

    @Test
    void unrelatedQuestionWithNoFaqMatchGetsTheFallback() {
        ChatController.ChatResponse response = ask("Tell me a joke about cats");
        assertNotNull(response.reply());
        assertFalse(response.reply().isBlank());
        verifyNoInteractions(chatClient);
    }
}