# exoplanets — agent

**Query:** Give me 5 confirmed exoplanets and their discovery method.

**Tool calls:** 1

**Trajectory:**
- getExoplanetArchive(select top 5 pl_name, discoverymethod from ps) -> Error communicating with NASA endpoint: Illegal character in query at index 117: https://exoplanetarchive.ipac.caltech.edu/TAP/sync?query=select+pl_name,discove

**Output:**

ERROR: java.lang.RuntimeException: An error occurred when calling the Gemini API endpoint.
