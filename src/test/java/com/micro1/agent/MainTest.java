package com.micro1.agent;


import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;


/**
 * These tests exercise the actual parsing/instrumentation logic shipped in Main and
 * NasaTools — not a reimplementation of it inline in the test, which is how the previous
 * version of this file worked and why it never caught real bugs.
 */
public class MainTest {

    @Test
    public void extractsTopicFromWellFormedTag() {
        String response = "[TOPIC: solar_flare_report]\n\nThree flares were recorded...";
        assertEquals("solar_flare_report", Main.extractTopic(response));
    }

    @Test
    public void fallsBackToGeneralMissionWhenTagIsMissing() {
        String response = "Sorry, I couldn't find that data.";
        assertEquals("general_mission", Main.extractTopic(response));
    }

    @Test
    public void fallsBackToGeneralMissionOnNullInput() {
        assertEquals("general_mission", Main.extractTopic(null));
    }

    @Test
    public void detectsClarificationTopicExactly() {
        String response = "[TOPIC: clarification_required]\n\nWhich rover did you mean?";
        assertEquals("clarification_required", Main.extractTopic(response));
    }

    @Test
    public void nasaToolsInstrumentationCountsRealCalls() {
        NasaTools tools = new NasaTools();
        assertEquals(0, tools.getToolCallCount());

        tools.calculateKineticEnergy(1000.0, 20.0);
        tools.calculateKineticEnergy(500.0, 15.0);

        assertEquals(2, tools.getToolCallCount());
        assertEquals(2, tools.getCallLog().size());
        assertTrue(tools.getCallLog().get(0).startsWith("calculateKineticEnergy("));
    }

    @Test
    public void resetInstrumentationClearsCountAndLog() {
        NasaTools tools = new NasaTools();
        tools.calculateKineticEnergy(1000.0, 20.0);
        tools.resetInstrumentation();

        assertEquals(0, tools.getToolCallCount());
        assertTrue(tools.getCallLog().isEmpty());
    }

    @Test
    public void kineticEnergyFormulaIsCorrect() {
        // 0.5 * m * v^2, v converted from km/s to m/s
        NasaTools tools = new NasaTools();
        double joules = tools.calculateKineticEnergy(2.0, 1.0); // 2 kg at 1 km/s = 1000 m/s
        assertEquals(0.5 * 2.0 * 1000.0 * 1000.0, joules, 0.0001);
    }
}
