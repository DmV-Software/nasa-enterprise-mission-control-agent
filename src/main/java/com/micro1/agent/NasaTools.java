package com.micro1.agent;


import dev.langchain4j.agent.tool.Tool;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;


public class NasaTools {

    // NASA_API_KEY is read once from the environment. DEMO_KEY is capped at ~30 req/hour and
    // ~50/day per IP, which is what caused the OVER_RATE_LIMIT reports already sitting in
    // reports/ from earlier testing. Set NASA_API_KEY in your environment before running.
    private static final String NASA_API_KEY = resolveNasaApiKey();

    private static String resolveNasaApiKey() {
        String key = System.getenv("NASA_API_KEY");
        if (key == null || key.trim().isEmpty()) {
            System.out.println("\u001B[33m[WARNING] NASA_API_KEY not set in environment — falling back to " +
                    "DEMO_KEY (heavily rate-limited: ~30 req/hour, ~50/day per IP). " +
                    "Set NASA_API_KEY to your own free key from https://api.nasa.gov for reliable eval runs.\u001B[00m");
            return "DEMO_KEY";
        }
        return key.trim();
    }

    // --- Instrumentation: turns "trust me, it called the tools" into evidence a judge can open. ---
    private int toolCallCount = 0;
    private final List<String> callLog = new ArrayList<>();

    public int getToolCallCount() {
        return toolCallCount;
    }

    public List<String> getCallLog() {
        return callLog;
    }

    public void resetInstrumentation() {
        toolCallCount = 0;
        callLog.clear();
    }

    private void record(String toolName, String args, String outputPreview) {
        toolCallCount++;
        String preview = outputPreview == null ? "null" :
                outputPreview.substring(0, Math.min(160, outputPreview.length()));
        callLog.add(toolName + "(" + args + ") -> " + preview.replace("\n", " "));
    }


    @Tool("Fetches Near Earth Objects (NEO) asteroid data from NASA for a specific date. Date format: YYYY-MM-DD.")
    public String getNearEarthAsteroids(String date) {
        System.out.println("[SYSTEM] Executing getNearEarthAsteroids for date: " + date);
        if (date == null || !date.matches("\\d{4}-\\d{2}-\\d{2}")) {
            record("getNearEarthAsteroids", String.valueOf(date), "ERROR: bad date format");
            return "ERROR: Invalid date format. Expected YYYY-MM-DD.";
        }
        String result = fetchFromUrl("https://api.nasa.gov/neo/rest/v1/feed?start_date=" + date + "&end_date=" + date + "&api_key=" + NASA_API_KEY);
        record("getNearEarthAsteroids", date, result);
        return result;
    }


    @Tool("Fetches NASA Astronomy Picture of the Day (APOD) for a given date. Date format: YYYY-MM-DD.")
    public String getAstronomyPictureOfTheDay(String date) {
        System.out.println("[SYSTEM] Executing getAstronomyPictureOfTheDay for date: " + date);
        if (date == null || !date.matches("\\d{4}-\\d{2}-\\d{2}")) {
            record("getAstronomyPictureOfTheDay", String.valueOf(date), "ERROR: bad date format");
            return "ERROR: Invalid date format. Expected YYYY-MM-DD.";
        }
        String result = fetchFromUrl("https://api.nasa.gov/planetary/apod?api_key=" + NASA_API_KEY + "&date=" + date);
        record("getAstronomyPictureOfTheDay", date, result);
        return result;
    }


    @Tool("Fetches photos taken by Mars rovers (Curiosity, Opportunity, Spirit) for a specific Earth date (YYYY-MM-DD).")
    public String getMarsRoverPhotos(String roverName, String earthDate) {
        System.out.println("[SYSTEM] Executing getMarsRoverPhotos for rover: " + roverName + " on date: " + earthDate);
        if (earthDate == null || !earthDate.matches("\\d{4}-\\d{2}-\\d{2}")) {
            record("getMarsRoverPhotos", roverName + "," + earthDate, "ERROR: bad date format");
            return "ERROR: Invalid date format. Expected YYYY-MM-DD.";
        }
        String result = fetchFromUrl("https://api.nasa.gov/mars-photos/api/v1/rovers/" + roverName.toLowerCase() + "/photos?earth_date=" + earthDate + "&api_key=" + NASA_API_KEY);
        record("getMarsRoverPhotos", roverName + "," + earthDate, result);
        return result;
    }


    @Tool("Fetches solar flare (FLR) space weather alerts from NASA DONKI database for a date range.")
    public String getSolarFlareData(String startDate, String endDate) {
        System.out.println("[SYSTEM] Executing getSolarFlareData from " + startDate + " to " + endDate);
        String result = fetchFromUrl("https://api.nasa.gov/DONKI/FLR?startDate=" + startDate + "&endDate=" + endDate + "&api_key=" + NASA_API_KEY);
        record("getSolarFlareData", startDate + "," + endDate, result);
        return result;
    }


    @Tool("Fetches geomagnetic storm (GST) alerts and metrics from NASA DONKI.")
    public String getGeomagneticStormData(String startDate, String endDate) {
        System.out.println("[SYSTEM] Executing getGeomagneticStormData from " + startDate + " to " + endDate);
        String result = fetchFromUrl("https://api.nasa.gov/DONKI/GST?startDate=" + startDate + "&endDate=" + endDate + "&api_key=" + NASA_API_KEY);
        record("getGeomagneticStormData", startDate + "," + endDate, result);
        return result;
    }


    @Tool("Fetches coronal mass ejection (CME) space weather reports from NASA DONKI.")
    public String getDonkiCmeData(String startDate, String endDate) {
        System.out.println("[SYSTEM] Executing getDonkiCmeData from " + startDate + " to " + endDate);
        String result = fetchFromUrl("https://api.nasa.gov/DONKI/CME?startDate=" + startDate + "&endDate=" + endDate + "&api_key=" + NASA_API_KEY);
        record("getDonkiCmeData", startDate + "," + endDate, result);
        return result;
    }


    @Tool("Fetches polychromatic natural color images of Earth (EPIC) from DSCOVR satellite for a specific date.")
    public String getEarthPolychromaticImaging(String date) {
        System.out.println("[SYSTEM] Executing getEarthPolychromaticImaging for date: " + date);
        String result = fetchFromUrl("https://api.nasa.gov/EPIC/api/natural/date/" + date + "?api_key=" + NASA_API_KEY);
        record("getEarthPolychromaticImaging", date, result);
        return result;
    }


    // Whitelist of real NASA Exoplanet Archive discoverymethod values. A whitelist (rather than
    // splicing the LLM's raw string into SQL) keeps this a safe, structured filter instead of a
    // TAP-injection surface, while still making the tool's declared parameter actually do something.
    private static final Set<String> KNOWN_DISCOVERY_METHODS = Set.of(
            "TRANSIT", "RADIAL VELOCITY", "IMAGING", "MICROLENSING", "ASTROMETRY",
            "TRANSIT TIMING VARIATIONS", "PULSAR TIMING", "ORBITAL BRIGHTNESS MODULATION",
            "DISK KINEMATICS", "ECLIPSE TIMING VARIATIONS"
    );

    @Tool("Searches the NASA Exoplanet Archive for confirmed exoplanets. Optionally filter by discovery " +
            "method — one of: Transit, Radial Velocity, Imaging, Microlensing, Astrometry, Transit Timing " +
            "Variations, Pulsar Timing. Pass an empty string for no filter.")
    public String getExoplanetArchive(String discoveryMethod) {
        System.out.println("[SYSTEM] Executing getExoplanetArchive with discoveryMethod: " + discoveryMethod);

        String normalized = discoveryMethod == null ? "" : discoveryMethod.trim().toUpperCase();

        // Build the ADQL as a plain, human-readable string first, THEN URL-encode the whole thing in
        // one pass. The previous version hand-concatenated some pieces raw (e.g. "rowid<=10") and
        // only URL-encoded the filter fragment — the unescaped '<' is not a legal URI character and
        // made URI.create() throw for every query with no discoveryMethod filter. Encoding the full
        // ADQL string at once means there is no piece left un-escaped, by construction.
        String rawAdql;
        if (!normalized.isEmpty() && KNOWN_DISCOVERY_METHODS.contains(normalized)) {
            // discoveryMethod is checked against a fixed whitelist above, so this can never contain
            // attacker/LLM-controlled SQL syntax — it's a safe, structured filter, not raw SQL.
            rawAdql = "select pl_name,discoverymethod,disc_year from ps where discoverymethod='" +
                    capitalizeWords(normalized) + "' order by disc_year desc";
        } else {
            rawAdql = "select pl_name,discoverymethod,disc_year from ps where rowid<=10";
        }

        String encodedQuery = URLEncoder.encode(rawAdql, StandardCharsets.UTF_8);
        String url = "https://exoplanetarchive.ipac.caltech.edu/TAP/sync?query=" + encodedQuery + "&format=json";

        String result = fetchFromUrl(url);
        record("getExoplanetArchive", discoveryMethod, result);
        return result;
    }

    private static String capitalizeWords(String upperCaseInput) {
        String[] words = upperCaseInput.toLowerCase().split(" ");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (w.isEmpty()) continue;
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(" ");
        }
        return sb.toString().trim();
    }


    @Tool("Searches NASA TechPort for space technology projects whose title or description contains the " +
            "given keyword. Pass an empty string to get the most recent projects unfiltered.")
    public String getTechPortProjects(String searchKeyword) {
        System.out.println("[SYSTEM] Executing getTechPortProjects for keyword: " + searchKeyword);

        // TechPort's public endpoint doesn't support a free-text query param, so the declared
        // "searchKeyword" filter is applied client-side against the returned project list rather
        // than silently ignored — the tool's description now matches what it actually does.
        String result = fetchFromUrl("https://techport.nasa.gov/api/projects?api_key=" + NASA_API_KEY);

        String finalResult = result;
        if (searchKeyword != null && !searchKeyword.trim().isEmpty() && result != null && !result.startsWith("NASA_API_ERROR")) {
            String needle = searchKeyword.trim().toLowerCase();
            StringBuilder filtered = new StringBuilder("[");
            boolean any = false;
            for (String line : result.split("\\},\\s*\\{")) {
                if (line.toLowerCase().contains(needle)) {
                    if (any) filtered.append(",");
                    filtered.append(line.startsWith("{") ? "" : "{").append(line).append(line.endsWith("}") ? "" : "}");
                    any = true;
                }
            }
            filtered.append("]");
            finalResult = any ? filtered.toString() :
                    "No TechPort projects matched keyword \"" + searchKeyword + "\" in this result page.";
        }

        record("getTechPortProjects", searchKeyword, finalResult);
        return finalResult;
    }


    @Tool("Calculates the kinetic energy of an object (in Joules) based on its mass (in kg) and velocity (in km/s).")
    public double calculateKineticEnergy(double massKg, double velocityKmPerSec) {
        System.out.println("[SYSTEM] Executing calculateKineticEnergy. Mass: " + massKg + ", Velocity: " + velocityKmPerSec);
        double velocityMetersPerSec = velocityKmPerSec * 1000.0;
        double joules = 0.5 * massKg * Math.pow(velocityMetersPerSec, 2);
        record("calculateKineticEnergy", massKg + "kg," + velocityKmPerSec + "km/s", String.valueOf(joules));
        return joules;
    }


    private String fetchFromUrl(String urlStr) {
        int maxAttempts = 3;
        int delayMs = 1500;
        Exception lastException = null;
        int lastStatus = -1;
        String lastErrorBody = null;

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(10))
                .build();

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(urlStr))
                        .timeout(java.time.Duration.ofSeconds(15))
                        // Some public/academic APIs (e.g. the Caltech-run Exoplanet Archive TAP
                        // service) are stricter about request headers than api.nasa.gov and will
                        // hang or reset a connection that looks like a bare bot request.
                        .header("User-Agent", "micro1-nasa-agent/1.0 (hackathon submission)")
                        .header("Accept", "application/json, text/plain, */*")
                        .GET().build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() >= 400) {
                    lastStatus = response.statusCode();
                    lastErrorBody = response.body();
                    if (attempt < maxAttempts) {
                        System.out.println("\u001B[33m[WARNING] NASA API returned status " + response.statusCode() +
                                ". Retrying (" + (attempt + 1) + "/" + maxAttempts + ")...\u001B[00m");
                        Thread.sleep(delayMs);
                        continue;
                    }
                    // Retries exhausted on an HTTP error — return an explicit, unmistakable error
                    // marker instead of the raw error JSON body, so the LLM can't mistake an error
                    // page for real NASA data (this was silently happening before this fix).
                    return "NASA_API_ERROR: status=" + lastStatus + " endpoint=" + urlStr +
                            " body_snippet=" + safeSnippet(lastErrorBody);
                }

                String body = response.body();
                if (body != null && body.length() > 4000) {
                    return body.substring(0, 4000) + "... [truncated for length]";
                }
                return body;
            } catch (Exception e) {
                lastException = e;
                // Full diagnostic to the console immediately — the eval transcript's 160-char
                // trajectory preview truncates this away (the URL alone eats most of that budget),
                // so without this line a network failure's real cause (timeout vs. TLS vs. DNS vs.
                // connection reset) was previously undiagnosable from the saved reports alone.
                System.out.println("\u001B[33m[NETWORK ERROR] " + e.getClass().getSimpleName() + ": " + e.getMessage() +
                        " (endpoint=" + urlStr + ", attempt " + attempt + "/" + maxAttempts + ")\u001B[00m");
                if (attempt < maxAttempts) {
                    System.out.println("\u001B[33m[WARNING] Retrying in " + delayMs + "ms (" + (attempt + 1) + "/" + maxAttempts + ")...\u001B[00m");
                    try {
                        Thread.sleep(delayMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        String causeDetail = lastException != null
                ? lastException.getClass().getSimpleName() + ": " + lastException.getMessage()
                : "unknown error";
        return "NASA_API_ERROR: network failure after " + maxAttempts + " attempts, cause=" + causeDetail +
                ", endpoint=" + urlStr;
    }

    private static String safeSnippet(String body) {
        if (body == null) return "none";
        return body.length() > 200 ? body.substring(0, 200) + "..." : body;
    }

}
