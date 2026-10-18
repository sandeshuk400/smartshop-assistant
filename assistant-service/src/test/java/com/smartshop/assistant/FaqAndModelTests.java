package com.smartshop.assistant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class FaqAndModelTests {

    private String readFaq() throws Exception {
        try (InputStream in = new ClassPathResource("faq.txt").getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void faqFileLoadsAndIsNotEmpty() throws Exception {
        assertFalse(readFaq().isBlank());
    }

    @Test
    void faqCoversShopLocationAndSupport() throws Exception {
        String faq = readFaq();
        assertTrue(faq.contains("Shop location and address"));
        assertTrue(faq.contains("Contact support"));
    }

    @Test
    void faqUsesDemoContactDetailsOnly() throws Exception {
        String faq = readFaq();
        assertTrue(faq.contains("support@example.com"));
        assertFalse(faq.contains("@gmail.com"));
    }

    @Test
    void chatRequestKeepsItsValues() {
        ChatController.ChatRequest request = new ChatController.ChatRequest("Hello", "session-1");
        assertEquals("Hello", request.message());
        assertEquals("session-1", request.sessionId());
    }

    @Test
    void chatResponseKeepsItsValue() {
        ChatController.ChatResponse response = new ChatController.ChatResponse("Hi there");
        assertEquals("Hi there", response.reply());
    }
}