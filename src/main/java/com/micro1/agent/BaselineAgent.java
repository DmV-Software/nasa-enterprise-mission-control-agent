package com.micro1.agent;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * Baseline for comparison against NasaAgent.
 * <p>
 * Represents the "one direct prompt with basic instructions" baseline described in the
 * hackathon brief: same LLM, same reference date, but NO tool access and NO live NASA data.
 * This is what a Space Systems Data Analyst gets by just asking a generic chatbot instead
 * of using the agent — no cross-endpoint orchestration, no live grounding, no structured export.
 */
public interface BaselineAgent {

    @SystemMessage({
            "You are a general-purpose assistant answering questions about NASA and space science.",
            "Current date is: {{current_date}}.",
            "You do NOT have access to any live tools, APIs, or the internet.",
            "Answer using only what you already know from training.",
            "If the question requires current, real-time, or date-specific data you cannot possibly know " +
            "(for example: today's Astronomy Picture of the Day, a live asteroid feed, a live solar flare alert), " +
            "say so plainly instead of inventing numbers, titles, filenames or facts."
    })
    String chat(@V("current_date") String currentDate, @UserMessage String userMessage);
}
