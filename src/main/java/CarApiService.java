import okhttp3.*;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class CarApiService {
    private final OkHttpClient client = new OkHttpClient();
    private final String userAgent;

    public CarApiService() {
        this.userAgent = Config.get("WIKIPEDIA_USER_AGENT", "CarFantasyBot/1.0");
    }

    // METODO 1: /dettagli - Scheda tecnica dettagliata
    public SearchResult getModelDetailsWithImage(String model) {
        try {
            String title = searchWikipedia(model);
            if (title == null) {
                return SearchResult.error("❌ Nessun risultato per " + model);
            }
            return fetchTechnicalDetails(title);
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
            SearchResult result = fetchTechnicalDetails(title);
            return result.hasError() ? result.getErrorMessage() : result.getCaption();
        } catch (Exception e) {
            return "❌ Errore: " + e.getMessage();
        }
    }

    // METODO 2: /cerca - Informazioni generali
    public SearchResult searchByMakeWithImage(String make) {
        try {
            String title = searchWikipedia(make);
            if (title == null) {
                return SearchResult.error("❌ Nessun risultato per " + make);
            }
            return fetchGeneralInfo(title);
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
            SearchResult result = fetchGeneralInfo(title);
            return result.hasError() ? result.getErrorMessage() : result.getCaption();
        } catch (Exception e) {
            return "❌ Errore: " + e.getMessage();
        }
    }

    // Metodi privati comuni
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

    // Metodo per /cerca - Informazioni generali
    private SearchResult fetchGeneralInfo(String title) throws IOException {
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

            // Costruisci risposta per /cerca
            StringBuilder caption = new StringBuilder("🔍 **" + title + "**\n\n");

            if (json.has("extract")) {
                String extract = json.get("extract").getAsString();
                caption.append("📝 **Informazioni generali:**\n");
                caption.append(extract.length() > 350 ? extract.substring(0, 350) + "..." : extract);

                // Aggiungi link per dettagli tecnici
                caption.append("\n\n📊 Per le specifiche tecniche usa: /dettagli ").append(title);
            } else {
                caption.append("📋 Nessuna informazione disponibile.");
            }

            // Estrai immagine
            String imageUrl = extractImageUrl(json);

            return imageUrl != null ?
                    SearchResult.successWithImage(imageUrl, caption.toString()) :
                    SearchResult.success(caption.toString());
        }
    }

    // Metodo per /dettagli - Scheda tecnica
    private SearchResult fetchTechnicalDetails(String title) throws IOException {
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
                return SearchResult.error("🚗 **" + title + "**\n\n⚙️ Dati tecnici non disponibili.");
            }

            String body = response.body().string();
            JsonObject json = JsonParser.parseString(body).getAsJsonObject();

            // Costruisci scheda tecnica
            StringBuilder caption = new StringBuilder("🚗 **" + title + "**\n\n");
            caption.append("⚙️ **SCHEDA TECNICA**\n\n");

            if (json.has("extract")) {
                String extract = json.get("extract").getAsString();

                // Estrai informazioni tecniche dal testo
                List<String> techSpecs = extractTechnicalSpecs(extract);

                if (!techSpecs.isEmpty()) {
                    for (String spec : techSpecs) {
                        caption.append("• ").append(spec).append("\n");
                    }
                } else {
                    // Se non troviamo specifiche tecniche precise
                    caption.append("📋 **Informazioni:**\n");
                    caption.append(extract.length() > 300 ? extract.substring(0, 300) + "..." : extract);
                    caption.append("\n\nℹ️ *Specifiche tecniche non disponibili nel riassunto*");
                }
            } else {
                caption.append("❌ Nessuna informazione tecnica disponibile.");
            }

            // Estrai immagine
            String imageUrl = extractImageUrl(json);

            return imageUrl != null ?
                    SearchResult.successWithImage(imageUrl, caption.toString()) :
                    SearchResult.success(caption.toString());
        }
    }

    // Estrai specifiche tecniche dal testo
    private List<String> extractTechnicalSpecs(String text) {
        List<String> specs = new ArrayList<>();

        if (text == null || text.isEmpty()) return specs;

        // Pattern per specifiche tecniche comuni
        String[] patterns = {
                "\\b\\d{3,5}\\s*(cm³|cc|ccm)\\b",  // Cilindrata
                "\\b\\d{2,4}\\s*(cv|CV|kW|hp|HP)\\b",  // Potenza
                "\\b\\d{1,3}[,\\.]\\d*\\s*litri\\b",  // Litri
                "\\b\\d{2,3}\\s*km/h\\b",  // Velocità
                "0-100.*?\\d+[,\\.]\\d*\\s*s",  // Accelerazione
                "\\b\\d+\\s*(porte|posti)\\b",  // Porte/posti
                "\\b\\d{4}\\s*[–\\-]\\s*\\d{4}\\b",  // Anni produzione
                "\\bV\\d+|\\d+\\s*cilindri\\b",  // Configurazione motore
                "\\b\\d{3,4}\\s*kg\\b",  // Peso
                "\\b\\d+\\s*Nm\\b"  // Coppia
        };

        for (String patternStr : patterns) {
            Pattern pattern = Pattern.compile(patternStr, Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(text);

            while (matcher.find() && specs.size() < 10) {
                String spec = matcher.group();
                if (!specs.contains(spec)) {
                    specs.add(formatSpec(spec));
                }
            }
        }

        return specs;
    }

    private String formatSpec(String spec) {
        // Formatta le specifiche in modo leggibile
        spec = spec.replace("cv", "CV")
                .replace("kw", "kW")
                .replace("hp", "HP");

        return spec;
    }

    // Estrai URL immagine
    private String extractImageUrl(JsonObject json) {
        try {
            if (json.has("thumbnail") && json.getAsJsonObject("thumbnail").has("source")) {
                return json.getAsJsonObject("thumbnail").get("source").getAsString();
            }
        } catch (Exception e) {
            System.err.println("Errore estrazione immagine: " + e.getMessage());
        }
        return null;
    }
}