package com.example.demo;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ModelCheck {
    public static void main(String[] args) {
        try {
            // 1. Read API Key from config/application.yml
            String apiKey = null;
            try (BufferedReader br = new BufferedReader(new FileReader("config/application.yml"))) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (line.trim().startsWith("key:")) {
                        apiKey = line.split("key:")[1].trim().replace("\"", "");
                        break;
                    }
                }
            }

            if (apiKey == null || apiKey.equals("YOUR_GEMINI_API_KEY_HERE")) {
                System.out.println("API Key not found or is still placeholder in config/application.yml");
                return;
            }

            System.out.println("Using API Key: " + apiKey.substring(0, 5) + "...");

            // 2. Call ListModels API
            String urlStr = "https://generativelanguage.googleapis.com/v1beta/models?key=" + apiKey;
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");

            int responseCode = conn.getResponseCode();
            System.out.println("Response Code: " + responseCode);

            BufferedReader in;
            if (responseCode >= 200 && responseCode < 300) {
                in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            } else {
                in = new BufferedReader(new InputStreamReader(conn.getErrorStream()));
            }

            String inputLine;
            StringBuilder response = new StringBuilder();
            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();

            String json = response.toString();
            System.out.println("Available Models (names only):");
            
            // Simple regex to find "name": "models/..."
            Pattern p = Pattern.compile("\"name\":\\s*\"([^\"]+)\"");
            Matcher m = p.matcher(json);
            while (m.find()) {
                System.out.println(m.group(1));
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
