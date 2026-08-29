package com.micro1.agent;


import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.service.AiServices;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Scanner;


public class Main {
    public static void main(String[] args) {
        String apiKey = System.getenv("GEMINI_API_KEY");
        if (apiKey == null || apiKey.trim().isEmpty()) {
            System.err.println("Error: GEMINI_API_KEY is not set!");
            return;
        }
        apiKey = apiKey.trim();

        ChatLanguageModel model = GoogleAiGeminiChatModel.builder()
                .apiKey(apiKey)
                .modelName("gemini-3.5-flash")
                .temperature(0.2)
                .build();

        NasaAgent agent = AiServices.builder(NasaAgent.class)
                .chatLanguageModel(model)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(15))
                .tools(new NasaTools())
                .build();

        String currentDate = LocalDate.now().format(DateTimeFormatter.ISO_DATE);
        System.out.println("🚀 NASA Enterprise 10-Tool Agent initialized. Reference date: " + currentDate);
        System.out.println("Type 'exit' to quit. Ask about asteroids, solar flares, Mars weather, exoplanets, or tech innovations.");

        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.print("\nCommand > ");
            String userInput = scanner.nextLine();

            if (userInput.trim().equalsIgnoreCase("exit")) {
                System.out.println("Shutting down NASA Agent...");
                break;
            }

            if (userInput.trim().isEmpty()) {
                continue;
            }

            try {
                System.out.println("[SYSTEM] Processing request...");
                String response = agent.chat(currentDate, userInput);
                System.out.println("\n" + response);
            } catch (Exception e) {
                System.err.println("Agent execution error (try again in a moment if 503 high demand occurs): " + e.getMessage());
            }
        }
        scanner.close();
    }


    public static String callAgentWithRetry(NasaAgent agent, String currentDate, String input) {
        int maxRetries = 3;
        long delayMs = 2000;
        for (int i = 1; i <= maxRetries; i++) {
            try {
                return agent.chat(currentDate, input);
            } catch (Exception e) {
                String msg = e.getMessage() != null ? e.getMessage() : "";
                if ((msg.contains("503") || msg.contains("UNAVAILABLE")) && i < maxRetries) {
                    System.out.println("\u001B[33m[WARNING] API overloaded (503). Retrying in " + (delayMs / 1000) + "s (Attempt " + i + "/" + maxRetries + ")...\u001B[00m");
                    try { Thread.sleep(delayMs); } catch (InterruptedException ignored) {}
                    delayMs *= 2;
                } else {
                    throw e;
                }
            }
        }
        throw new RuntimeException("Failed after max retries due to server overload.");
    }


    public static void saveReportToFile(String reportContent) {
        try {
            String filename = "nasa_mission_report_" + System.currentTimeMillis() + ".md";
            java.nio.file.Files.writeString(java.nio.file.Path.of(filename), reportContent);
            System.out.println("\u001B[32m[SYSTEM] Report successfully exported to local file: " + filename + "\u001B[00m");
        } catch (Exception e) {
            System.err.println("Error saving report to file: " + e.getMessage());
        }
    }


    public static void runEvaluationSuite(NasaAgent agent, String currentDate) {
        System.out.println("\n\u001B[36m=== RUNNING AUTOMATED EVALUATION SUITE (EVALS) ===\u001B[00m");
        String[] testPrompts = {
                "Give me a photo from red planet",
                "Check asteroids for " + currentDate,
                "What is the kinetic energy for a 500000 kg asteroid moving at 15 km/s?"
        };

        int passed = 0;
        for (int i = 0; i < testPrompts.length; i++) {
            System.out.println("\n[TEST " + (i + 1) + "] Input: \"" + testPrompts[i] + "\"");
            try {
                String res = agent.chat(currentDate, testPrompts[i]);
                System.out.println("\u001B[32m[PASSED] Response generated successfully (" + res.length() + " chars).\u001B[00m");
                passed++;
            } catch (Exception e) {
                System.err.println("\u001B[31m[FAILED] Error: " + e.getMessage() + "\u001B[00m");
            }
        }
        System.out.println("\n\u001B[36m=== EVALUATION COMPLETE: " + passed + "/" + testPrompts.length + " PASSED ===\u001B[00m\n");
    }

}