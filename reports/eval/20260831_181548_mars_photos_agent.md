# mars_photos — agent

**Query:** Show me photos taken by the Curiosity rover on 2026-08-20.

**Expected tools:** getMarsRoverPhotos

**Tool calls:** 1

**Trajectory:**
- getMarsRoverPhotos(Curiosity,2026-08-20) -> NASA_API_ERROR: status=404 endpoint=https://api.nasa.gov/mars-photos/api/v1/rovers/curiosity/photos?earth_date=2026-08-20&api_key=70nfAvDbJHIb5yRvoaFX4Eh2r5qQMG

**Output:**

ERROR: java.lang.RuntimeException: An error occurred when calling the Gemini API endpoint.
