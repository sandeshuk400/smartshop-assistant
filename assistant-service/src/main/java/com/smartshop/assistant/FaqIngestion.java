package com.smartshop.assistant;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

@Component
public class FaqIngestion implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(FaqIngestion.class);

    private final VectorStore vectorStore;

    public FaqIngestion(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        String text = StreamUtils.copyToString(
                new ClassPathResource("faq.txt").getInputStream(), StandardCharsets.UTF_8);

        List<Document> documents = Arrays.stream(text.split("(\\r?\\n){2,}"))
                .map(String::trim)
                .filter(chunk -> !chunk.isEmpty())
                .map(chunk -> new Document(
                        UUID.nameUUIDFromBytes(chunk.getBytes(StandardCharsets.UTF_8)).toString(),
                        chunk,
                        java.util.Map.of("source", "faq.txt")))
                .toList();

        // Same text gives the same id, so restarting updates rows instead of duplicating them.
        vectorStore.add(documents);
        log.info("Loaded {} FAQ chunks into the vector store", documents.size());
    }
}