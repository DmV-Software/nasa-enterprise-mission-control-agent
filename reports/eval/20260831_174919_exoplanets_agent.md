# exoplanets — agent

**Query:** Give me 5 confirmed exoplanets and their discovery method.

**Expected tools:** getExoplanetArchive

**Tool calls:** 3

**Trajectory:**
- getExoplanetArchive() -> NASA_API_ERROR: status=400 endpoint=https://exoplanetarchive.ipac.caltech.edu/TAP/sync?query=select+pl_name%2Cdiscoverymethod%2Cdisc_year+from+ps+where+rowid%3C
- getExoplanetArchive(Transit) -> [ {"pl_name": "TOI-159 b", "discoverymethod": "Transit", "disc_year": 2026}, {"pl_name": "TOI-5926 b", "discoverymethod": "Transit", "disc_year": 2026}, {"pl_na
- getExoplanetArchive() -> NASA_API_ERROR: status=400 endpoint=https://exoplanetarchive.ipac.caltech.edu/TAP/sync?query=select+pl_name%2Cdiscoverymethod%2Cdisc_year+from+ps+where+rowid%3C

**Output:**

ERROR: java.lang.RuntimeException: An error occurred when calling the Gemini API endpoint.
