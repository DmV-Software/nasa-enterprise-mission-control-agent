package com.micro1.agent;


import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.service.AiServices;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import java.util.stream.Collectors;


public class Main {

    /**
     * Model fallback chain, tried in order for every request. If the primary model returns
     * 429 / 503 / RESOURCE_EXHAUSTED / a quota error across ALL available API keys, the whole
     * chain-of-keys retry is repeated on the next model down before giving up entirely.
     * Index 0 is primary; used by EvalRunner too, so baseline vs agent stays a fair comparison
     * (both go through the same resilience layer).
     */
    public static final String[] MODEL_FALLBACK_CHAIN = {
            "gemini-3.5-flash",
            "gemini-3.5-flash-lite",
            "gemini-3.1-flash-lite"
    };

    private static final boolean QUIET = !"false".equalsIgnoreCase(System.getenv("AGENT_QUIET"));

    private static List<String> apiKeys;
    private static int currentKeyIndex = 0;

    /** Builds a ChatLanguageModel for a given model name + key, then invokes the caller with it. */
    @FunctionalInterface
    public interface ChatCaller {
        String call(ChatLanguageModel model, String currentDate, String userMessage) throws Exception;
    }

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
        System.out.println("---------------------------------------------------");
        System.out.println("       NASA ENTERPRISE MISSION CONTROL AGENT       ");
        System.out.println("---------------------------------------------------");
        System.out.println("\u001B[00m");
        System.out.println("\u001B[32m[STATUS] Secure API Key Pool Active (" + apiKeys.size() + " keys loaded)\u001B[00m");
        System.out.println("\u001B[32m[STATUS] Model fallback chain: " + String.join(" -> ", MODEL_FALLBACK_CHAIN) + "\u001B[00m");
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

            if (userInput.trim().equalsIgnoreCase("evals")) {
                EvalRunner.run(apiKeys, currentDate);
                continue;
            }

            if (userInput.trim().isEmpty()) {
                continue;
            }

            try {
                System.out.println("\u001B[36m[SYSTEM] Processing request...\u001B[00m");
                String response = callAgentWithKeyRotation(currentDate, userInput);
                System.out.println("\n" + response);
                saveReportToFile(response, userInput);
            } catch (Exception e) {
                String fallbackResponse = "[TOPIC: uplink_failure]\n\n" +
                        "**[CRITICAL SYSTEM ALERT]** Mission Control has lost uplink with the core scientific intelligence module.\n" +
                        "**Diagnostic:** All secure API keys AND all fallback models (" + String.join(", ", MODEL_FALLBACK_CHAIN) + ") " +
                        "are temporarily exhausted due to rate limit restrictions.\n" +
                        "**Action Required:** Please stand by and re-transmit your query in a few moments once the network window resets.";

                System.out.println("\n" + fallbackResponse);
                logTrajectory(userInput, "rate_limit_error", "UPLINK_FAILURE_HANDLED"); }
        }
        scanner.close();
    }


    /**
     * Runs one chat turn through the full resilience layer: for each model in
     * MODEL_FALLBACK_CHAIN (best first), tries every available API key (rotating on
     * rate-limit-style errors) before giving up on that model and dropping to the next one.
     * Non-rate-limit errors (bad request, auth failure, etc.) are NOT retried — they propagate
     * immediately, since retrying a broken request across every model/key combination would
     * just waste quota on an error that won't fix itself.
     */
    public static String callWithFallback(String currentDate, String userMessage, ChatCaller caller) {
        Exception lastError = null;

        for (String modelName : MODEL_FALLBACK_CHAIN) {
            int attempts = 0;
            int maxAttempts = apiKeys.size();

            while (attempts < maxAttempts) {
                String activeKey = apiKeys.get(currentKeyIndex);
                try {
                    ChatLanguageModel chatModel = GoogleAiGeminiChatModel.builder()
                            .apiKey(activeKey)
                            .modelName(modelName)
                            .temperature(0.2)
                            .build();

                    String result = caller.call(chatModel, currentDate, userMessage);

                    if (!modelName.equals(MODEL_FALLBACK_CHAIN[0])) {
                        System.out.println("\u001B[33m[SYSTEM] Served by fallback model: " + modelName + "\u001B[00m");
                    }
                    return result;
                } catch (Exception e) {
                    lastError = e;
                    String msg = e.getMessage() != null ? e.getMessage() : "";
                    // 429/503/quota = capacity problem (this key/model is temporarily maxed out).
                    // 403/PERMISSION_DENIED = access problem, but often PER-KEY: not every API key
                    // is necessarily enabled for every model (different free-tier projects can have
                    // different model access). Both cases are worth trying the next key for — only a
                    // clearly non-recoverable error (bad request shape, network parse failure, etc.)
                    // should skip straight to propagating without burning through the whole pool.
                    boolean isRetryable = msg.contains("429") || msg.contains("RESOURCE_EXHAUSTED")
                            || msg.contains("503") || msg.toLowerCase().contains("quota")
                            || msg.contains("403") || msg.contains("PERMISSION_DENIED");

                    if (!isRetryable) {
                        // Not an access/capacity problem (e.g. malformed request) — don't burn
                        // through every model/key combination retrying something that can't succeed.
                        throw new RuntimeException(e);
                    }

                    if (apiKeys.size() > 1) {
                        if (!QUIET) {
                            String reason = (msg.contains("403") || msg.contains("PERMISSION_DENIED"))
                                    ? "was denied access (403)" : "hit a rate limit";
                            System.out.println("\u001B[33m[WARNING] Key #" + (currentKeyIndex + 1) + " on model " + modelName +
                                    " " + reason + ". Rotating to next API key...\u001B[00m");
                        }
                        currentKeyIndex = (currentKeyIndex + 1) % apiKeys.size();
                        attempts++;
                    } else {
                        break; // only one key — no point looping, drop straight to the next model
                    }
                }
            }
            System.out.println("\u001B[33m[WARNING] Model " + modelName + " exhausted across all available keys" +
                    (QUIET ? " (" + apiKeys.size() + "/" + apiKeys.size() + " denied/limited)" : "") +
                    ". Falling back to next model in chain...\u001B[00m");
        }

        throw new RuntimeException("All models (" + String.join(", ", MODEL_FALLBACK_CHAIN) +
                ") and API keys exhausted.", lastError);
    }


    public static String callAgentWithKeyRotation(String currentDate, String input) {
        return callWithFallback(currentDate, input, (model, date, msg) -> {
            NasaAgent agent = AiServices.builder(NasaAgent.class)
                    .chatLanguageModel(model)
                    .chatMemory(MessageWindowChatMemory.withMaxMessages(15))
                    .tools(new NasaTools())
                    .build();
            return agent.chat(date, msg);
        });
    }


    /**
     * Pulls the "[TOPIC: some_topic]" tag the agent is instructed to put on the first line
     * of every response (see NasaAgent's system prompt). Falls back to "general_mission" if
     * the tag is missing or malformed, so a report is never lost just because the LLM
     * didn't follow formatting instructions exactly.
     */
    public static String extractTopic(String reportContent) {
        if (reportContent == null) {
            return "general_mission";
        }
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("^\\[TOPIC:\\s*([a-zA-Z0-9_]+)\\]");
        java.util.regex.Matcher matcher = pattern.matcher(reportContent.trim());
        if (matcher.find()) {
            return matcher.group(1).toLowerCase();
        }
        return "general_mission";
    }


    public static void saveReportToFile(String reportContent, String userInput) {
        String filename = "";
        try {
            java.nio.file.Path reportsDir = java.nio.file.Path.of("reports");
            if (!java.nio.file.Files.exists(reportsDir)) {
                java.nio.file.Files.createDirectories(reportsDir);
            }

            String topic = extractTopic(reportContent);

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
