package com.example.smartmanager.ai;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class OpenRouterServiceClientTests {

    private final OpenRouterServiceClient client = new OpenRouterServiceClient(new com.fasterxml.jackson.databind.ObjectMapper());

    @Test
    void testParseSubtasksCleanJson() {
        String json = "[\"Task 1\", \"Task 2\", \"Task 3\"]";
        List<String> result = client.parseSubtasks(json);
        assertEquals(3, result.size());
        assertEquals("Task 1", result.get(0));
    }

    @Test
    void testParseSubtasksMarkdownWrapped() {
        String markdown = "```json\n[\"Setup API\", \"Write Docs\"]\n```";
        List<String> result = client.parseSubtasks(markdown);
        assertEquals(2, result.size());
        assertEquals("Setup API", result.get(0));
    }

    @Test
    void testParseSubtasksFallbackText() {
        String text = "1. Setup backend\n2. Setup frontend\n3. Write tests";
        List<String> result = client.parseSubtasks(text);
        assertEquals(3, result.size());
        assertEquals("Setup backend", result.get(0));
    }
}
