# space_weather_briefing — agent

**Query:** Give me a space weather risk briefing for 2026-08-20 to 2026-08-27 — flag any correlated events.

**Tool calls:** 3

**Trajectory:**
- getSolarFlareData(2026-08-20,2026-08-27) -> upstream connect error or disconnect/reset before headers. retried and the latest reset reason: remote connection failure
- getDonkiCmeData(2026-08-20,2026-08-27) -> [{"activityID":"2026-08-20T04:36:00-CME-001","catalog":"M2M_CATALOG","startTime":"2026-08-20T04:36Z","instruments":[{"displayName":"SOHO: LASCO/C2"},{"displayNa
- getGeomagneticStormData(2026-08-20,2026-08-27) -> upstream connect error or disconnect/reset before headers. reset reason: remote connection failure

**Output:**

ERROR: java.lang.RuntimeException: An error occurred when calling the Gemini API endpoint.
