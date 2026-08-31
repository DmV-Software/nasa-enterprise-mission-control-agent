# space_weather_briefing — agent

**Query:** Give me a space weather risk briefing for 2026-08-20 to 2026-08-27 — flag any correlated events.

**Expected tools:** getSolarFlareData, getDonkiCmeData, getGeomagneticStormData

**Tool calls:** 3

**Trajectory:**
- getSolarFlareData(2026-08-20,2026-08-27) -> [{"flrID":"2026-08-20T07:31:00-FLR-001","catalog":"M2M_CATALOG","instruments":[{"displayName":"GOES-P: EXIS 1.0-8.0"}],"beginTime":"2026-08-20T07:31Z","peakTime
- getDonkiCmeData(2026-08-20,2026-08-27) -> [{"activityID":"2026-08-20T04:36:00-CME-001","catalog":"M2M_CATALOG","startTime":"2026-08-20T04:36Z","instruments":[{"displayName":"SOHO: LASCO/C2"},{"displayNa
- getGeomagneticStormData(2026-08-20,2026-08-27) -> []

**Output:**

[TOPIC: space_weather_risk_briefing]

# Space Weather Risk Briefing (2026-08-20 to 2026-08-27)

Based on NASA DONKI observational feeds retrieved for the period between August 20, 2026, and August 27, 2026, solar activity was moderately active, featuring several M-class solar flares, associated coronal mass ejections (CMEs), and solar energetic particle (SEP) events.

## Executive Summary & Correlated Events

Active Region **AR 14513** served as the primary driver for major space weather activity during this window, producing multiple M-class flares and linked CMEs. 

### Key Correlated Event Sequences:
1. **August 20, 2026 (AR 14513 Eruption):**
   - **Solar Flare:** An **M8.1 flare** peaked at 11:42 UTC from source location N05E80 (AR 14513).
   - **Correlated CME:** Directly linked to CME ID `2026-08-20T12:15:00-CME-001` (speed ~694 km/s), which triggered WSA-ENLIL modeled impacts on Solar Orbiter, Europa Clipper, and OSIRIS-APEX.
2. **August 21, 2026 (Complex Multi-Phenomena Event):**
   - **Solar Flare:** An **M1.6 flare** occurred at 10:18 UTC from active region AR 14507 (N04W88).
   - **Correlated CME & SEPs:** This event was directly linked to a subsequent CME (`2026-08-21T09:36:00-CME-001`) and triggered **two distinct Solar Energetic Particle (SEP)** events (`2026-08-21T17:33:00-SEP-001` and `2026-08-22T22:28:00-SEP-001`).
3. **August 24–25, 2026 (Recurring AR 14513 Activity):**
   - **Solar Flares:** AR 14513 continued to rotate into view/earth-facing disk, producing an **M1.7 flare** on August 24 at 19:36 UTC (linked to CME `2026-08-24T22:36:00-CME-001`), followed by an **M1.9 flare** on August 25 at 01:26 UTC and an **M1.0 flare** at 04:02 UTC.

---

## Chronological Event Breakdown

### Solar Flares (FLR)
* **2026-08-20:** M1.8 (08:05 UTC, AR 14513) | **M8.1 (11:42 UTC, AR 14513)** | M2.9 (16:11 UTC, AR 14513)
* **2026-08-21:** M1.6 (10:18 UTC, AR 14507)
* **2026-08-24:** M1.7 (19:36 UTC, AR 14513)
* **2026-08-25:** M1.9 (01:26 UTC, AR 14513) | C4.9 (03:47 UTC, AR 14513) | M1.0 (04:02 UTC, AR 14513)

### Coronal Mass Ejections (CME) & Impacts
* **2026-08-20 (04:36 UTC):** Source from former AR 14510; speed ~459 km/s. Modeled impacts reached Mars (August 24) and Psyche (August 25).
* **2026-08-20 (12:15 UTC):** Linked to the M8.1 flare from AR 14513; speed ~694 km/s. Modeled impacts reached Solar Orbiter, Europa Clipper, and OSIRIS-APEX.

### Geomagnetic Storms (GST)
* **Geomagnetic Status:** No significant geomagnetic storm alerts or major Kp-index threshold disruptions were reported by DONKI for Earth during this specific timeframe, keeping terrestrial magnetospheric impact low despite multiple limb/disk events.
