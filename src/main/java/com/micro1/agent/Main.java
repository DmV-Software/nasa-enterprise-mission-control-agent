package com.micro1.agent;


import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.service.AiServices;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import java.util.stream.Collectors;


public class Main {
    private static List<String> apiKeys;
    private static int currentKeyIndex = 0;

    public static void main(String[] args) {
        String rawKeys = System.getenv("GEMINI_API_KEYS");
        if (rawKeys == null || rawKeys.trim().isEmpty()) {
            rawKeys = System.getenv("GEMINI_API_KEY");
        }

        if (rawKeys == null || rawKeys.trim().isEmpty()) {
            System.out.println("GEMINI_API_KEYS not found in environment. Please enter your Gemini API key(s) separated by comma:");
            Scanner inputScanner = new Scanner(System.in);
            rawKeys = inputScanner.nextLine().trim();
        }

        apiKeys = Arrays.stream(rawKeys.split(","))
                .map(String::trim)
                .filter(k -> !k.isEmpty())
                .collect(Collectors.toList());

        if (apiKeys.isEmpty()) {
            System.err.println("Error: No valid API keys provided!");
            return;
        }

        String currentDate = LocalDate.now().format(DateTimeFormatter.ISO_DATE);
        System.out.println("\u001B[32m🚀 NASA Enterprise Agent initialized with " + apiKeys.size() + " API key(s) in pool. Date: " + currentDate + "\u001B[00m");
        System.out.println("Type 'exit' to quit.");

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
                System.out.println("\u001B[36m[SYSTEM] Processing request via Key #" + (currentKeyIndex + 1) + "...\u001B[00m");
                String response = callAgentWithKeyRotation(currentDate, userInput);
                System.out.println("\n" + response);
                saveReportToFile(response);
            } catch (Exception e) {
                System.err.println("\u001B[31mAll keys exhausted or critical error: " + e.getMessage() + "\u001B[00m");
            }
        }
        scanner.close();
    }


    public static NasaAgent createAgent(String apiKey) {
        ChatLanguageModel model = GoogleAiGeminiChatModel.builder()
                .apiKey(apiKey)
                .modelName("gemini-3.5-flash")
                .temperature(0.2)
                .build();

        return AiServices.builder(NasaAgent.class)
                .chatLanguageModel(model)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(15))
                .tools(new NasaTools())
                .build();
    }


    public static String callAgentWithKeyRotation(String currentDate, String input) {
        int attempts = 0;
        int maxAttempts = apiKeys.size();

        while (attempts < maxAttempts) {
            String activeKey = apiKeys.get(currentKeyIndex);
            try {
                NasaAgent agent = createAgent(activeKey);
                return agent.chat(currentDate, input);
            } catch (Exception e) {
                String msg = e.getMessage() != null ? e.getMessage() : "";
                boolean isRateLimit = msg.contains("429") || msg.contains("RESOURCE_EXHAUSTED") || msg.contains("503");

                if (isRateLimit && apiKeys.size() > 1) {
                    System.out.println("\u001B[33m[WARNING] Key #" + (currentKeyIndex + 1) + " hit rate limit. Rotating to next API key instantly...\u001B[00m");
                    currentKeyIndex = (currentKeyIndex + 1) % apiKeys.size();
                    attempts++;
                } else {
                    throw e;
                }
            }
        }
        throw new RuntimeException("All API keys in the rotation pool are exhausted.");
    }


    public static void saveReportToFile(String reportContent) {
        try {
            String filename = "nasa_mission_report_" + System.currentTimeMillis() + ".md";
            Files.writeString(Path.of(filename), reportContent);
            System.out.println("\u001B[32m[SYSTEM] Report successfully exported to local file: " + filename + "\u001B[00m");
        } catch (Exception e) {
            System.err.println("Error saving report to file: " + e.getMessage());
        }
    }
}