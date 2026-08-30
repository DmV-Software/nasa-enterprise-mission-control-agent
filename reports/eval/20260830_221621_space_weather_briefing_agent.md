# space_weather_briefing — agent

**Query:** Give me a space weather risk briefing for 2026-08-20 to 2026-08-27 — flag any correlated events.

**Tool calls:** 3

**Trajectory:**
- getSolarFlareData(2026-08-20,2026-08-27) -> [{"flrID":"2026-08-20T07:31:00-FLR-001","catalog":"M2M_CATALOG","instruments":[{"displayName":"GOES-P: EXIS 1.0-8.0"}],"beginTime":"2026-08-20T07:31Z","peakTime
- getDonkiCmeData(2026-08-20,2026-08-27) -> upstream connect error or disconnect/reset before headers. retried and the latest reset reason: remote connection failure
- getGeomagneticStormData(2026-08-20,2026-08-27) -> []

**Output:**

ERROR: java.lang.RuntimeException: PERMISSION_DENIED (code 403) Your project has been denied access. Please contact support.
