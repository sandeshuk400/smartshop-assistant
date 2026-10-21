package com.smartshop.assistant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.vectorstore.VectorStore;

import jakarta.servlet.http.HttpServletRequest;

class OrderFlowTests {

    private OrderRepository orders;
    private ChatController controller;
    private HttpServletRequest http;

    @BeforeEach
    void setUp() {
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.Builder builder = mock(ChatClient.Builder.class, Answers.RETURNS_SELF);
        when(builder.build()).thenReturn(chatClient);

        orders = mock(OrderRepository.class);
        controller = new ChatController(builder, mock(VectorStore.class), orders);

        http = mock(HttpServletRequest.class);
        when(http.getRemoteAddr()).thenReturn("127.0.0.1");
    }

    private String ask(String message, String sessionId) {
        return controller.chat(new ChatController.ChatRequest(message, sessionId), http).reply();
    }

    @Test
    void orderQuestionAsksForCustomerId() {
        String reply = ask("Where is my order 123?", "s1");
        assertTrue(reply.contains("customer ID"));
        verifyNoInteractions(orders);
    }

    @Test
    void customerIdIsFollowedByARequestForTheBillCode() {
        ask("Where is my order 123?", "s1");
        String reply = ask("45", "s1");
        assertTrue(reply.contains("6-digit code"));
        verifyNoInteractions(orders);
    }

    @Test
    void wrongDetailsAreRejectedAfterOneLookup() {
        ask("Where is my order 123?", "s1");
        ask("45", "s1");
        String reply = ask("code 123456", "s1");

        assertTrue(reply.contains("couldn't find an order"));
        verify(orders).findByIdCustomerAndCode(123L, 45L, "123456");
    }

    @Test
    void sessionsAreKeptSeparate() {
        ask("Where is my order 123?", "session-a");
        String reply = ask("45", "session-b");
        assertFalse(reply.contains("6-digit code"));
    }

    @Test
    void repeatedWrongGuessesLockOutTheAddrpZEAWYtiB6bJ16NuLbGCc6CZ6jJdKfb63() {
        ask("Where is my order 123?", "s1");

        boolean lockedOut = false;
        for (int attempt = 0; attempt < 50 && !lockedOut; attempt++) {
            ask("45", "s1");
            String reply = ask("code 123456", "s1");
            lockedOut = reply.contains("Too many unsuccessful attempts");
        }
        assertTrue(lockedOut);

        clearInvocations(orders);
        String reply = ask("code 123456", "s1");
        assertTrue(reply.contains("Too many unsuccessful attempts"));
        verifyNoInteractions(orders);
    }
}