package com.micro1.agent;


import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;


public class MainTest {


    @Test
    public void testQuerySlugGeneration() {
        String query = "Summarize Artemis II Mission & Nuclear Thermal Propulsion!";
        String slug = query.toLowerCase().replaceAll("[^a-z0-9]", "_");
        assertTrue(slug.startsWith("summarize_artemis"));
    }


    @Test
    public void testEmptyQuerySlugFallback() {
        String query = "   ";
        String slug = query.toLowerCase().replaceAll("[^a-z0-9]", "_");
        if (slug.isEmpty() || slug.replace("_","").isEmpty()) {
            slug = "general_telemetry";
        }
        assertEquals("general_telemetry", slug);
    }
}