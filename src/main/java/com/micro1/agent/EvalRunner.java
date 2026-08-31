package com.micro1.agent;

import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.service.AiServices;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Baseline-vs-agent evaluation harness.
 * <p>
 * Runs a fixed, DETERMINISTIC set of representative research queries (all pinned to fixed
 * calendar dates, not "today" — a benchmark that changes answer depending on when you run it
 * isn't reproducible) through:
 *   (A) BaselineAgent — plain LLM, zero tools, zero live NASA data
 *   (B) NasaAgent     — the full 10-tool orchestrated agent
 * <p>
 * Both variants go through Main.callWithFallback, so the comparison stays fair on resilience:
 * same model fallback chain, same key rotation layer on both sides. Latency and tool-call counts
 * are reported for transparency, but are NOT treated as correctness metrics — an agent turn does
 * strictly more work (HTTP calls + a second LLM synthesis pass) than a single baseline completion,
 * so higher agent latency is an expected cost of live grounding, not a regression.
 * <p>
 * Each case also declares expectedTools: the automated PASS/FAIL check verifies the agent called
 * (at least) the tools the task actually requires — this is a tool-selection correctness check,
 * not a semantic/factual correctness grader. Whether the final prose is fully accurate still needs
 * a human reviewer reading the transcript; this harness tells you whether the agent even reached
 * for the right instruments before writing that prose.
 */
public class EvalRunner {

    private record EvalCase(String id, String query, List<String> expectedTools) {}

    private static final List<EvalCase> CASES = List.of(
            new EvalCase("apod_fixed_date",
                    "What was NASA's Astronomy Picture of the Day on 2026-08-20, including title and explanation?",
                    List.of("getAstronomyPictureOfTheDay")),
            new EvalCase("neo_feed",
                    "List the near-Earth asteroids approaching on 2026-08-30 and their closest approach distance.",
                    List.of("getNearEarthAsteroids")),
            new EvalCase("neo_plus_kinetic",
                    "Take the largest near-Earth asteroid approaching on 2026-08-30 and estimate the kinetic energy of an impact at its recorded velocity. Rank it in a threat table.",
                    List.of("getNearEarthAsteroids", "calculateKineticEnergy")),
            new EvalCase("mars_photos",
                    "Show me photos taken by the Curiosity rover on 2026-08-20.",
                    List.of("getMarsRoverPhotos")),
            new EvalCase("space_weather_briefing",
                    "Give me a space weather risk briefing for 2026-08-20 to 2026-08-27 — flag any correlated events.",
                    List.of("getSolarFlareData", "getDonkiCmeData", "getGeomagneticStormData")),
            new EvalCase("space_digest_fixed_date",
                    "Give me the space digest for 2026-08-20.",
                    List.of("getAstronomyPictureOfTheDay", "getMarsRoverPhotos", "getEarthPolychromaticImaging")),
            new EvalCase("exoplanets",
                    "Give me 5 confirmed exoplanets and their discovery method.",
                    List.of("getExoplanetArchive")),
            new EvalCase("ambiguous",
                    "Show me some space photos.",
                    List.of()), // no single mandatory tool — tests judgment, not a fixed call
            new EvalCase("out_of_domain",
                    "What's a good recipe for chocolate chip cookies?",
                    List.of()) // must call ZERO NASA tools; checked separately below
    );

    public static void run(List<String> apiKeys, String currentDate) {
        System.out.println("\n[EVAL] Running baseline-vs-agent comparison on " + CASES.size() + " cases...");

        Path evalDir = Path.of("reports", "eval");
        try {
            Files.createDirectories(evalDir);
        } catch (Exception e) {
            System.err.println("[EVAL] Could not create reports/eval: " + e.getMessage());
            return;
        }

        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        StringBuilder summary = new StringBuilder();
        summary.append("# Baseline vs Agent — Evaluation Run ").append(stamp).append("\n\n");
        summary.append("Reference date: ").append(currentDate).append("\n\n");
        summary.append("Model fallback chain: ").append(String.join(" -> ", Main.MODEL_FALLBACK_CHAIN)).append("\n\n");
        summary.append("Note: this harness checks tool-selection correctness (did the agent call the tools " +
                "the task requires) and compares tool usage, execution latency and generated outputs between " +
                "baseline and agent. It does not automatically grade factual/semantic correctness of the " +
                "final prose — that still needs a human reading the transcript.\n\n");
        summary.append("| Case | Tool-selection | Baseline tools | Agent tools | Baseline latency (ms) | Agent latency (ms) | Baseline preview | Agent preview |\n");
        summary.append("|---|---|---|---|---|---|---|---|\n");

        NasaTools sharedTools = new NasaTools();
        int passCount = 0;

        for (EvalCase c : CASES) {
            // --- Baseline: zero tools, zero live data ---
            long t0 = System.currentTimeMillis();
            String baselineOutput;
            try {
                baselineOutput = Main.callWithFallback(currentDate, c.query(), (model, date, msg) -> {
                    BaselineAgent baseline = AiServices.builder(BaselineAgent.class)
                            .chatLanguageModel(model)
                            .build();
                    return baseline.chat(date, msg);
                });
            } catch (Exception e) {
                baselineOutput = "ERROR: " + e.getMessage();
            }
            long baselineLatency = System.currentTimeMillis() - t0;

            // --- Agent: full 10-tool orchestration, instrumented ---
            sharedTools.resetInstrumentation();
            long t1 = System.currentTimeMillis();
            String agentOutput;
            try {
                agentOutput = Main.callWithFallback(currentDate, c.query(), (model, date, msg) -> {
                    NasaAgent agent = AiServices.builder(NasaAgent.class)
                            .chatLanguageModel(model)
                            .chatMemory(MessageWindowChatMemory.withMaxMessages(15))
                            .tools(sharedTools)
                            .build();
                    return agent.chat(date, msg);
                });
            } catch (Exception e) {
                agentOutput = "ERROR: " + e.getMessage();
            }
            long agentLatency = System.currentTimeMillis() - t1;
            int agentToolCalls = sharedTools.getToolCallCount();

            List<String> calledToolNames = extractToolNames(sharedTools.getCallLog());
            String verdict = evaluateToolSelection(c, calledToolNames);
            if (verdict.equals("PASS")) passCount++;

            summary.append("| ").append(c.id())
                    .append(" | ").append(verdict)
                    .append(" | 0 | ").append(agentToolCalls)
                    .append(" | ").append(baselineLatency)
                    .append(" | ").append(agentLatency)
                    .append(" | ").append(preview(baselineOutput))
                    .append(" | ").append(preview(agentOutput))
                    .append(" |\n");

            writeTranscript(evalDir, stamp, c.id(), "baseline", c.query(), baselineOutput, 0, List.of(), null);
            writeTranscript(evalDir, stamp, c.id(), "agent", c.query(), agentOutput, agentToolCalls, sharedTools.getCallLog(), c.expectedTools());

            System.out.println("[EVAL] " + c.id() + " done — verdict=" + verdict + ", agent_tools=" + calledToolNames);
        }

        summary.append("\n**Tool-selection score: ").append(passCount).append("/").append(CASES.size()).append(" cases PASS.**\n");

        String filename = "summary_" + stamp + ".md";

        Path summaryPath = evalDir.resolve(filename);
        try {
            Files.writeString(summaryPath, summary.toString());
            System.out.println("[EVAL] Summary written to " + summaryPath + " — tool-selection score: " + passCount + "/" + CASES.size());
        } catch (Exception e) {
            System.err.println("[EVAL] Could not write summary: " + e.getMessage());
        }
        try {
            java.io.File mdFile = summaryPath.toFile();
            if (java.awt.Desktop.isDesktopSupported()) {
                java.awt.Desktop.getDesktop().open(mdFile);
            }
        } catch (Exception ex) {
            System.out.println("\u001B[33m[WARNING] OS auto-open bypassed (headless mode).\u001B[00m");
        }
    }

    /** Pulls the tool name (text before the first '(') out of each "toolName(args) -> preview" log entry. */
    private static List<String> extractToolNames(List<String> callLog) {
        return callLog.stream()
                .map(entry -> entry.contains("(") ? entry.substring(0, entry.indexOf('(')) : entry)
                .collect(Collectors.toList());
    }

    /**
     * out_of_domain is special-cased: PASS means calling ZERO NASA tools (correctly recognizing the
     * request is out of scope). ambiguous has no fixed expected set — it's a judgment-call case,
     * always reported as INFO rather than PASS/FAIL. Everything else PASSes only if every tool in
     * expectedTools was actually called at least once.
     */
    private static String evaluateToolSelection(EvalCase c, List<String> calledToolNames) {
        if (c.id().equals("out_of_domain")) {
            return calledToolNames.isEmpty() ? "PASS" : "FAIL (called a NASA tool for an out-of-domain request)";
        }
        if (c.expectedTools().isEmpty()) {
            return "INFO (judgment case, no fixed expected tool)";
        }
        Set<String> called = Set.copyOf(calledToolNames);
        List<String> missing = c.expectedTools().stream().filter(t -> !called.contains(t)).collect(Collectors.toList());
        return missing.isEmpty() ? "PASS" : "FAIL (missing: " + String.join(", ", missing) + ")";
    }

    private static String preview(String text) {
        if (text == null) return "";
        String flat = text.replace("\n", " ").replace("|", "/");
        return flat.length() > 80 ? flat.substring(0, 80) + "..." : flat;
    }

    private static void writeTranscript(Path dir, String stamp, String caseId, String variant,
                                         String query, String output, int toolCalls, List<String> callLog,
                                         List<String> expectedTools) {
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(caseId).append(" — ").append(variant).append("\n\n");
        sb.append("**Query:** ").append(query).append("\n\n");
        if (expectedTools != null) {
            sb.append("**Expected tools:** ").append(expectedTools.isEmpty() ? "(none fixed — judgment case)" : String.join(", ", expectedTools)).append("\n\n");
        }
        sb.append("**Tool calls:** ").append(toolCalls).append("\n\n");
        if (!callLog.isEmpty()) {
            sb.append("**Trajectory:**\n");
            for (String entry : callLog) {
                sb.append("- ").append(entry).append("\n");
            }
            sb.append("\n");
        }
        sb.append("**Output:**\n\n").append(output).append("\n");

        Path file = dir.resolve(stamp + "_" + caseId + "_" + variant + ".md");
        try {
            Files.writeString(file, sb.toString(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (Exception e) {
            System.err.println("[EVAL] Could not write transcript " + file + ": " + e.getMessage());
        }
    }
}
