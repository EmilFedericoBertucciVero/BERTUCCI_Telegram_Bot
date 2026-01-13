import okhttp3.*;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class CarApiService {
    private final OkHttpClient client = new OkHttpClient();
    private final String userAgent;

    public CarApiService() {
        this.userAgent = Config.get("WIKIPEDIA_USER_AGENT", "CarFantasyBot/1.0");
    }

    // Metodi principali per il bot
    public SearchResult getModelDetailsWithImage(String model) {
        try {
            String title = searchWikipedia(model);
            if (title == null) {
                return SearchResult.error("❌ Nessun risultato per " + model);
            }
            return fetchCarDetails(title);
        } catch (Exception e) {
            return SearchResult.error("❌ Errore: " + e.getMessage());
        }
    }

    public String getModelDetails(String model) {
        try {
            String title = searchWikipedia(model);
            if (title == null) {
                return "❌ Nessun risultato per " + model;
            }
            SearchResult result = fetchCarDetails(title);
            return result.hasError() ? result.getErrorMessage() : result.getCaption();
        } catch (Exception e) {
            return "❌ Errore: " + e.getMessage();
        }
    }

    public SearchResult searchByMakeWithImage(String make) {
        try {
            String title = searchWikipedia(make);
            if (title == null) {
                return SearchResult.error("❌ Nessun risultato per " + make);
            }
            return fetchCarDetails(title);
        } catch (Exception e) {
            return SearchResult.error("❌ Errore: " + e.getMessage());
        }
    }

    public String searchByMake(String make) {
        try {
            String title = searchWikipedia(make);
            if (title == null) {
                return "❌ Nessun risultato per " + make;
            }
            SearchResult result = fetchCarDetails(title);
            return result.hasError() ? result.getErrorMessage() : result.getCaption();
        } catch (Exception e) {
            return "❌ Errore: " + e.getMessage();
        }
    }

    // Metodi privati semplificati
    private String searchWikipedia(String query) throws IOException {
        query = query.trim();

        // Prova prima italiano, poi inglese
        String title = searchInLanguage(query, "it");
        return title != null ? title : searchInLanguage(query, "en");
    }

    private String searchInLanguage(String query, String lang) throws IOException {
        String url = String.format(
                "https://%s.wikipedia.org/w/api.php?action=query&list=search&srsearch=%s&format=json",
                lang, URLEncoder.encode(query, StandardCharsets.UTF_8)
        );

        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) return null;

            String body = response.body().string();
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();

            if (!root.has("query")) return null;

            JsonObject queryObj = root.getAsJsonObject("query");
            JsonArray results = queryObj.getAsJsonArray("search");

            return results.size() > 0 ?
                    results.get(0).getAsJsonObject().get("title").getAsString() : null;
        }
    }

    private SearchResult fetchCarDetails(String title) throws IOException {
        // Ottieni riassunto e immagine
        String summaryUrl = String.format(
                "https://it.wikipedia.org/api/rest_v1/page/summary/%s",
                URLEncoder.encode(title.replace(" ", "_"), StandardCharsets.UTF_8)
        );

        Request request = new Request.Builder()
                .url(summaryUrl)
                .header("User-Agent", userAgent)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                return SearchResult.error("🚗 **" + title + "**\n\n📋 Dati non disponibili.");
            }

            String body = response.body().string();
            JsonObject json = JsonParser.parseString(body).getAsJsonObject();

            // Costruisci la risposta
            StringBuilder caption = new StringBuilder("🚗 **" + title + "**\n\n");

            if (json.has("extract")) {
                String extract = json.get("extract").getAsString();
                caption.append("📋 **Informazioni:**\n");
                caption.append(extract.length() > 300 ? extract.substring(0, 300) + "..." : extract);
            } else {
                caption.append("📋 Nessuna informazione disponibile.");
            }

            // Estrai immagine se disponibile
            String imageUrl = null;
            if (json.has("thumbnail") && json.getAsJsonObject("thumbnail").has("source")) {
                imageUrl = json.getAsJsonObject("thumbnail").get("source").getAsString();
            }

            return imageUrl != null ?
                    SearchResult.successWithImage(imageUrl, caption.toString()) :
                    SearchResult.success(caption.toString());
        }
    }
}