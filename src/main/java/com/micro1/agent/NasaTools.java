package com.micro1.agent;


import dev.langchain4j.agent.tool.Tool;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;


public class NasaTools {


    @Tool("Fetches Near Earth Objects (NEO) asteroid data from NASA for a specific date. Date format: YYYY-MM-DD.")
    public String getNearEarthAsteroids(String date) {
        System.out.println("[SYSTEM] Executing getNearEarthAsteroids for date: " + date);
        if (date == null || !date.matches("\\d{4}-\\d{2}-\\d{2}")) {
            return "ERROR: Invalid date format. Expected YYYY-MM-DD.";
        }
        return fetchFromUrl("https://api.nasa.gov/neo/rest/v1/feed?start_date=" + date + "&end_date=" + date + "&api_key=DEMO_KEY");
    }


    @Tool("Fetches NASA Astronomy Picture of the Day (APOD) for a given date. Date format: YYYY-MM-DD.")
    public String getAstronomyPictureOfTheDay(String date) {
        System.out.println("[SYSTEM] Executing getAstronomyPictureOfTheDay for date: " + date);
        if (date == null || !date.matches("\\d{4}-\\d{2}-\\d{2}")) {
            return "ERROR: Invalid date format. Expected YYYY-MM-DD.";
        }
        return fetchFromUrl("https://api.nasa.gov/planetary/apod?api_key=DEMO_KEY&date=" + date);
    }


    @Tool("Fetches photos taken by Mars rovers (Curiosity, Opportunity, Spirit) for a specific Earth date (YYYY-MM-DD).")
    public String getMarsRoverPhotos(String roverName, String earthDate) {
        System.out.println("[SYSTEM] Executing getMarsRoverPhotos for rover: " + roverName + " on date: " + earthDate);
        if (earthDate == null || !earthDate.matches("\\d{4}-\\d{2}-\\d{2}")) {
            return "ERROR: Invalid date format. Expected YYYY-MM-DD.";
        }
        return fetchFromUrl("https://api.nasa.gov/mars-photos/api/v1/rovers/" + roverName.toLowerCase() + "/photos?earth_date=" + earthDate + "&api_key=DEMO_KEY");
    }


    @Tool("Fetches solar flare (FLR) space weather alerts from NASA DONKI database for a date range.")
    public String getSolarFlareData(String startDate, String endDate) {
        System.out.println("[SYSTEM] Executing getSolarFlareData from " + startDate + " to " + endDate);
        return fetchFromUrl("https://api.nasa.gov/DONKI/FLR?startDate=" + startDate + "&endDate=" + endDate + "&api_key=DEMO_KEY");
    }


    @Tool("Fetches geomagnetic storm (GST) alerts and metrics from NASA DONKI.")
    public String getGeomagneticStormData(String startDate, String endDate) {
        System.out.println("[SYSTEM] Executing getGeomagneticStormData from " + startDate + " to " + endDate);
        return fetchFromUrl("https://api.nasa.gov/DONKI/GST?startDate=" + startDate + "&endDate=" + endDate + "&api_key=DEMO_KEY");
    }


    @Tool("Fetches coronal mass ejection (CME) space weather reports from NASA DONKI.")
    public String getDonkiCmeData(String startDate, String endDate) {
        System.out.println("[SYSTEM] Executing getDonkiCmeData from " + startDate + " to " + endDate);
        return fetchFromUrl("https://api.nasa.gov/DONKI/CME?startDate=" + startDate + "&endDate=" + endDate + "&api_key=DEMO_KEY");
    }


    @Tool("Fetches polychromatic natural color images of Earth (EPIC) from DSCOVR satellite for a specific date.")
    public String getEarthPolychromaticImaging(String date) {
        System.out.println("[SYSTEM] Executing getEarthPolychromaticImaging for date: " + date);
        return fetchFromUrl("https://api.nasa.gov/EPIC/api/natural/date/" + date + "?api_key=DEMO_KEY");
    }


    @Tool("Searches NASA Exoplanet Archive for confirmed exoplanet data using a SQL-like query parameter.")
    public String getExoplanetArchive(String queryCriteria) {
        System.out.println("[SYSTEM] Executing getExoplanetArchive with criteria: " + queryCriteria);
        return fetchFromUrl("https://exoplanetarchive.ipac.caltech.edu/TAP/sync?query=select+pl_name,discoverymethod,disc_year+from+ps+where+rowid<=5&format=json");
    }


    @Tool("Searches NASA TechPort database for cutting-edge space technology project innovations.")
    public String getTechPortProjects(String searchKeyword) {
        System.out.println("[SYSTEM] Executing getTechPortProjects for keyword: " + searchKeyword);
        return fetchFromUrl("https://techport.nasa.gov/api/projects?api_key=DEMO_KEY");
    }


    @Tool("Calculates the kinetic energy of an object (in Joules) based on its mass (in kg) and velocity (in km/s).")
    public double calculateKineticEnergy(double massKg, double velocityKmPerSec) {
        System.out.println("[SYSTEM] Executing calculateKineticEnergy. Mass: " + massKg + ", Velocity: " + velocityKmPerSec);
        double velocityMetersPerSec = velocityKmPerSec * 1000.0;
        return 0.5 * massKg * Math.pow(velocityMetersPerSec, 2);
    }


    private String fetchFromUrl(String urlStr) {
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(urlStr)).GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            String body = response.body();
            if (body != null && body.length() > 4000) {
                return body.substring(0, 4000) + "... [truncated for length]";
            }
            return body;
        } catch (Exception e) {
            return "Error communicating with NASA endpoint: " + e.getMessage();
        }
    }
}