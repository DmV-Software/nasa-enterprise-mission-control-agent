# space_weather_briefing — baseline

**Query:** Give me a space weather risk briefing for 2026-08-20 to 2026-08-27 — flag any correlated events.

**Tool calls:** 0

**Output:**

I cannot provide a specific space weather risk briefing or flag correlated events for the week of August 20 to August 27, 2026. Because I do not have access to live data feeds, real-time APIs, or retrospective space weather databases, I cannot look up the actual solar activity, geomagnetic storm indices (such as the Kp-index), or coronal mass ejection (CME) alerts that occurred during that timeframe. 

To get the actual historical data and archived briefings for those dates, you can consult the **NOAA Space Weather Prediction Center (SWPC)** archives or the **NASA Space Weather Database Of Notifications, Knowledge, Information (DONKI)**.

However, to help you understand how space weather risks are assessed and how events correlate, here is a guide on how space weather forecasters identify and flag correlated events during a typical briefing:

---

### How Space Weather Events Correlate

Space weather typically follows a predictable chain of causality originating at the Sun. When analyzing a weekly risk briefing, forecasters look for the following correlated progression:

#### 1. The Trigger: Solar Flares (R-Scale / Radio Blackouts)
*   **What it is:** A sudden flash of increased brightness on the Sun, usually near active sunspot regions, releasing massive amounts of electromagnetic radiation (X-rays and extreme UV).
*   **Correlation:** Travel time to Earth is only **8 minutes** (speed of light). 
*   **Impact:** Causes immediate ionization of the upper atmosphere, leading to High Frequency (HF) radio blackouts on the sunlit side of Earth. This is rated on the NOAA **R-Scale (R1 to R5)**.

#### 2. The Fast Follower: Solar Radiation Storms (S-Scale / Solar Proton Events)
*   **What it is:** Highly energetic charged particles (mostly protons) accelerated by the solar flare or the shockwave of an associated CME.
*   **Correlation:** Travel time to Earth is **tens of minutes to a few hours**. 
*   **Impact:** These particles penetrate Earth's magnetic field near the poles. They pose radiation hazards to astronauts, high-altitude commercial flights, and polar-route aviation communications. This is rated on the NOAA **S-Scale (S1 to S5)**.

#### 3. The Main Event: Coronal Mass Ejections & Geomagnetic Storms (G-Scale)
*   **What it is:** A massive bubble of solar plasma and magnetic fields ejected into space. 
*   **Correlation:** Travel time to Earth is **1 to 3 days** (though exceptionally fast CMEs can arrive in under 18 hours). 
*   **Impact:** When the CME's magnetic field collides with Earth's magnetosphere, it causes a **Geomagnetic Storm**, rated on the NOAA **G-Scale (G1 to G5)**. 
    *   *Correlated Risks:* Induced currents in power grids, satellite drag and charging, GPS/GNSS degradation, and brilliant auroral displays pushed to lower latitudes than normal.

### Example of a "Correlated Event" Flag in a Briefing
If a forecaster observed a major event, the briefing would link them like this:
*   **Day 1:** *Active Region AR3000 produced an X-class solar flare (R3 Strong Radio Blackout) at 12:00 UTC.*
*   **Day 1 (Hours later):** *An associated S2 (Moderate) Solar Radiation Storm is currently underway, impacting polar aviation routes.*
*   **Day 3 (Expected):** *A halo Coronal Mass Ejection (CME) observed in coronagraph imagery is modeled to arrive on Day 3, with a high probability of triggering a G3 (Strong) to G4 (Severe) Geomagnetic Storm. Power grid operators and satellite operators should prepare for mitigation protocols.*
