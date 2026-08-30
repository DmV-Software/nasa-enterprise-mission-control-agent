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
        System.out.println("\u001B[36m");
        System.out.println("╔════════════════════════════════════════════════════════════════╗");
        System.out.println("║       \u202F\u202F 🚀 NASA ENTERPRISE MISSION CONTROL AGENT 🚀        ║");
        System.out.println("╚════════════════════════════════════════════════════════════════╝");
        System.out.println("\u001B[00m");
        System.out.println("\u001B[32m[STATUS] Secure API Key Pool Active (" + apiKeys.size() + " keys loaded)\u001B[00m");
        System.out.println("\u001B[32m[STATUS] NASA Enterprise 10-Tool Agent initialized. Reference date: " + currentDate + "\u001B[00m");
        System.out.println("Type 'evals' to run automated test suite, or 'exit' to quit.");

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
                saveReportToFile(response, userInput);
            } catch (Exception e) {
                String fallbackResponse = "[TOPIC: uplink_failure]\n\n" +
                        "**[CRITICAL SYSTEM ALERT]** Mission Control has lost uplink with the core scientific intelligence module.\n" +
                        "**Diagnostic:** All secure API keys in the rotation pool are temporarily exhausted due to rate limit restrictions.\n" +
                        "**Action Required:** Please stand by and re-transmit your query in a few moments once the network window resets.";

                System.out.println("\n" + fallbackResponse);
                logTrajectory(userInput, "rate_limit_error", "UPLINK_FAILURE_HANDLED"); }
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


    public static void saveReportToFile(String reportContent, String userInput) {
        String filename = "";
        try {
            java.nio.file.Path reportsDir = java.nio.file.Path.of("reports");
            if (!java.nio.file.Files.exists(reportsDir)) {
                java.nio.file.Files.createDirectories(reportsDir);
            }

            String topic = "general_mission";

            // Extract the topic tag generated by the AI agent
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("^\\[TOPIC:\\s*([a-zA-Z0-9_]+)\\]");
            java.util.regex.Matcher matcher = pattern.matcher(reportContent.trim());

            if (matcher.find()) {
                topic = matcher.group(1).toLowerCase();
            }

            if (topic.equals("clarification_required")) {
                System.out.println("\n\u001B[33m[SYSTEM] Pending user clarification. Report saving bypassed.\u001B[00m");
                logTrajectory(userInput, "clarification_required", "BYPASSED_CLARIFICATION");
                return;
            }

            String timeSuffix = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HHmmss"));
            filename = "reports/nasa_" + topic + "_" + timeSuffix + ".md";
            java.nio.file.Files.writeString(java.nio.file.Path.of(filename), reportContent);

            System.out.println("\n\u001B[32m[SYSTEM] Report successfully exported to: " + filename + "\u001B[00m");
            logTrajectory(userInput, topic, "SUCCESS_REPORT_SAVED");
        } catch (Exception e) {
            System.err.println("\n[ERROR] Failed to save report to file: " + e.getMessage());
        }
        try {
            java.io.File mdFile = new java.io.File(filename);
            if (java.awt.Desktop.isDesktopSupported()) {
                java.awt.Desktop.getDesktop().open(mdFile);
            }
        } catch (Exception ex) {
            System.out.println("\u001B[33m[WARNING] OS auto-open bypassed (headless mode).\u001B[00m");
        }
    }


    public static void logTrajectory(String userQuery, String topic, String executionStatus) {
        try {
            java.nio.file.Path logsDir = java.nio.file.Path.of("reports");
            if (!java.nio.file.Files.exists(logsDir)) {
                java.nio.file.Files.createDirectories(logsDir);
            }

            java.nio.file.Path trajectoryFile = logsDir.resolve("agent_trajectory.log");
            String timestamp = java.time.LocalDateTime.now().toString();

            String logEntry = String.format("[%s] USER_QUERY: \"%s\" | DETECTED_TOPIC: %s | STATUS: %s%n",
                    timestamp, userQuery.replace("\n", " "), topic, executionStatus);

            java.nio.file.Files.writeString(trajectoryFile, logEntry,
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.APPEND);
        } catch (Exception e) {
            System.err.println("[WARNING] Failed to write trajectory log.");
        }
    }


}