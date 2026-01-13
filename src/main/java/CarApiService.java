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
                caption.append("📖 **Informazioni generali:**\n");
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

    // Metodo per /dettagli - Scheda tecnica CON INFOBOX
    private SearchResult fetchTechnicalDetails(String title) throws IOException {
        // PRIMA: Ottieni l'immagine dal summary
        String imageUrl = fetchImageFromSummary(title);

        // SECONDA: Ottieni i dati della infobox
        Map<String, String> infoboxData = fetchInfoboxData(title);

        // TERZA: Se non troviamo infobox, prova a cercare in inglese
        if (infoboxData.isEmpty()) {
            String enTitle = searchInLanguage(title, "en");
            if (enTitle != null && !enTitle.equals(title)) {
                infoboxData = fetchInfoboxDataFromLang(enTitle, "en");
            }
        }

        // Costruisci scheda tecnica (SENZA Markdown per evitare errori di parsing)
        StringBuilder caption = new StringBuilder("🚗 " + title.toUpperCase() + "\n\n");
        caption.append("⚙️ SCHEDA TECNICA\n\n");

        if (!infoboxData.isEmpty()) {
            // Solo i campi che ci interessano, in ordine
            String[] preferredFields = {
                    // Configurazione base
                    "carrozzeria",
                    "posizione motore",
                    "trazione",
                    // Peso
                    "peso a vuoto",
                    "massa a vuoto",
                    "peso",
                    // Motore
                    "tipomotore",
                    "cilindrata",
                    "potenza",
                    "coppia",
                    // Prestazioni
                    "velocità",
                    "accelerazione"
            };

            int count = 0;
            // Mostra solo i campi nell'ordine specificato
            for (String field : preferredFields) {
                if (infoboxData.containsKey(field)) {
                    String value = infoboxData.get(field);
                    String fieldName = formatFieldName(field);
                    caption.append("• ").append(fieldName)
                            .append(": ").append(value).append("\n");
                    infoboxData.remove(field);
                    count++;
                }
            }

            // Se non abbiamo trovato abbastanza dati, aggiungi nota
            if (count < 5) {
                caption.append("\nℹ️ Alcuni dati tecnici potrebbero non essere disponibili per questo modello.");
            }
        } else {
            // Fallback: usa il summary come prima
            String summary = fetchSummaryText(title);
            if (summary != null && !summary.isEmpty()) {
                caption.append("📋 **Informazioni:**\n");
                caption.append(summary.length() > 400 ? summary.substring(0, 400) + "..." : summary);
                caption.append("\n\nℹ️ *Infobox non disponibile per questo modello*");
            } else {
                caption.append("❌ Nessuna informazione tecnica disponibile.\n");
                caption.append("ℹ️ *Prova con un nome più specifico del modello*");
            }
        }

        return imageUrl != null ?
                SearchResult.successWithImage(imageUrl, caption.toString()) :
                SearchResult.success(caption.toString());
    }

    // Fetch immagine dal summary API
    private String fetchImageFromSummary(String title) throws IOException {
        String summaryUrl = String.format(
                "https://it.wikipedia.org/api/rest_v1/page/summary/%s",
                URLEncoder.encode(title.replace(" ", "_"), StandardCharsets.UTF_8)
        );

        Request request = new Request.Builder()
                .url(summaryUrl)
                .header("User-Agent", userAgent)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) return null;

            String body = response.body().string();
            JsonObject json = JsonParser.parseString(body).getAsJsonObject();
            return extractImageUrl(json);
        } catch (Exception e) {
            return null;
        }
    }

    // Fetch dati dalla infobox usando parse API
    private Map<String, String> fetchInfoboxData(String title) throws IOException {
        return fetchInfoboxDataFromLang(title, "it");
    }

    // Fetch dati dalla infobox con specifica lingua
    private Map<String, String> fetchInfoboxDataFromLang(String title, String lang) throws IOException {
        Map<String, String> data = new LinkedHashMap<>();

        // Usa l'API parse per ottenere il wikitext
        String parseUrl = String.format(
                "https://%s.wikipedia.org/w/api.php?action=parse&page=%s&prop=wikitext&format=json",
                lang, URLEncoder.encode(title, StandardCharsets.UTF_8)
        );

        Request request = new Request.Builder()
                .url(parseUrl)
                .header("User-Agent", userAgent)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) return data;

            String body = response.body().string();
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();

            if (!root.has("parse")) return data;

            JsonObject parseObj = root.getAsJsonObject("parse");
            if (!parseObj.has("wikitext")) return data;

            String wikitext = parseObj.getAsJsonObject("wikitext").get("*").getAsString();

            // Estrai dati dalla infobox
            data = parseInfobox(wikitext);

        } catch (Exception e) {
            System.err.println("Errore parsing infobox: " + e.getMessage());
        }

        return data;
    }

    // Fetch summary text
    private String fetchSummaryText(String title) throws IOException {
        String summaryUrl = String.format(
                "https://it.wikipedia.org/api/rest_v1/page/summary/%s",
                URLEncoder.encode(title.replace(" ", "_"), StandardCharsets.UTF_8)
        );

        Request request = new Request.Builder()
                .url(summaryUrl)
                .header("User-Agent", userAgent)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) return null;

            String body = response.body().string();
            JsonObject json = JsonParser.parseString(body).getAsJsonObject();

            if (json.has("extract")) {
                return json.get("extract").getAsString();
            }
        } catch (Exception e) {
            System.err.println("Errore fetch summary: " + e.getMessage());
        }

        return null;
    }

    // Parse della tabella caratteristiche tecniche dal wikitext
    private Map<String, String> parseInfobox(String wikitext) {
        Map<String, String> data = new LinkedHashMap<>();

        try {
            // Cerca prima il template {{Auto-caratteristiche}} che contiene i dati tecnici dettagliati
            Pattern techPattern = Pattern.compile(
                    "\\{\\{Auto-caratteristiche\\s*\\n(.*?)\\n\\}\\}",
                    Pattern.CASE_INSENSITIVE | Pattern.DOTALL
            );

            Matcher techMatcher = techPattern.matcher(wikitext);

            if (techMatcher.find()) {
                String techContent = techMatcher.group(1);
                System.out.println("Template Auto-caratteristiche trovato, lunghezza: " + techContent.length());
                data = parseTechSpecs(techContent);
            }

            // Se non troviamo Auto-caratteristiche, prova con l'infobox base come fallback
            if (data.isEmpty()) {
                System.out.println("Auto-caratteristiche non trovato, provo con template Auto...");
                Pattern autoPattern = Pattern.compile(
                        "\\{\\{Auto\\s*\\n(.*?)\\n\\}\\}",
                        Pattern.CASE_INSENSITIVE | Pattern.DOTALL
                );

                Matcher autoMatcher = autoPattern.matcher(wikitext);

                if (autoMatcher.find()) {
                    String autoContent = autoMatcher.group(1);
                    System.out.println("Template Auto trovato, lunghezza: " + autoContent.length());
                    data = parseBasicTemplate(autoContent);
                }
            }

            System.out.println("Campi estratti totali: " + data.size());

        } catch (Exception e) {
            System.err.println("Errore nel parsing: " + e.getMessage());
            e.printStackTrace();
        }

        return data;
    }

    // Parse del template Auto-caratteristiche (dati tecnici dettagliati)
    private Map<String, String> parseTechSpecs(String content) {
        Map<String, String> data = new LinkedHashMap<>();

        // Pattern per catturare i campi del template
        Pattern fieldPattern = Pattern.compile(
                "\\|\\s*([^=\\|]+?)\\s*=\\s*([^\\n\\|]*)",
                Pattern.MULTILINE
        );

        Matcher fieldMatcher = fieldPattern.matcher(content);

        while (fieldMatcher.find()) {
            String key = fieldMatcher.group(1).trim();
            String value = fieldMatcher.group(2).trim();

            // Pulisci il valore
            value = cleanWikiText(value);

            // Ignora campi vuoti e commenti
            if (key.isEmpty() || value.isEmpty() ||
                    value.equals("-") || value.equals("–") ||
                    value.length() < 1 || value.startsWith("<!--")) {
                continue;
            }

            // Normalizza la chiave
            key = key.toLowerCase()
                    .replace("_", " ")
                    .replaceAll("\\s+", " ")
                    .trim();

            // Tronca valori troppo lunghi
            if (value.length() > 150) {
                value = value.substring(0, 147) + "...";
            }

            data.put(key, value);
        }

        return data;
    }

    // Parse del template Auto base (fallback)
    private Map<String, String> parseBasicTemplate(String content) {
        Map<String, String> data = new LinkedHashMap<>();

        Pattern fieldPattern = Pattern.compile(
                "\\|\\s*([^=\\|]+?)\\s*=\\s*([^\\n\\|]*)",
                Pattern.MULTILINE
        );

        Matcher fieldMatcher = fieldPattern.matcher(content);

        while (fieldMatcher.find()) {
            String key = fieldMatcher.group(1).trim();
            String value = fieldMatcher.group(2).trim();

            value = cleanWikiText(value);

            if (key.isEmpty() || value.isEmpty() ||
                    value.equals("-") || value.equals("–") ||
                    value.length() < 2 || value.startsWith("<!--")) {
                continue;
            }

            key = key.toLowerCase()
                    .replace("_", " ")
                    .replaceAll("\\s+", " ")
                    .trim();

            if (value.length() > 100) {
                value = value.substring(0, 97) + "...";
            }

            data.put(key, value);
        }

        return data;
    }

    // Pulisce il wikitext da markup wiki
    private String cleanWikiText(String text) {
        if (text == null || text.isEmpty()) return "";

        // Rimuovi commenti HTML
        text = text.replaceAll("<!--.*?-->", "");

        // Rimuovi tag ref
        text = text.replaceAll("<ref[^>]*>.*?</ref>", "");
        text = text.replaceAll("<ref[^>]*/>", "");
        text = text.replaceAll("<ref[^>]*>", "");
        text = text.replaceAll("</ref>", "");

        // Rimuovi template {{cita...}}
        text = text.replaceAll("\\{\\{[Cc]ita[^}]*\\}\\}", "");

        // Rimuovi altri template comuni
        text = text.replaceAll("\\{\\{[^}]+\\}\\}", "");

        // Rimuovi nowiki
        text = text.replaceAll("</?nowiki/?>", "");

        // Rimuovi link interni ma mantieni il testo visualizzato
        // [[testo]] -> testo
        // [[Link|testo]] -> testo
        text = text.replaceAll("\\[\\[(?:[^\\|\\]]+\\|)?([^\\]]+)\\]\\]", "$1");

        // Rimuovi link esterni
        text = text.replaceAll("\\[http[^\\]]+\\]", "");

        // Rimuovi markup grassetto/corsivo
        text = text.replaceAll("'{2,}", "");

        // Rimuovi multipli spazi e newline
        text = text.replaceAll("\\s+", " ");
        text = text.replaceAll("\\n+", " ");

        return text.trim();
    }

    // Formatta il nome del campo in modo leggibile
    private String formatFieldName(String field) {
        if (field == null || field.isEmpty()) return field;

        // Mappa solo dei campi che ci interessano
        Map<String, String> fieldNames = new HashMap<>();

        fieldNames.put("carrozzeria", "Carrozzeria");
        fieldNames.put("posizione motore", "Posizione motore");
        fieldNames.put("trazione", "Trazione");
        fieldNames.put("peso a vuoto", "Peso");
        fieldNames.put("massa a vuoto", "Peso");
        fieldNames.put("peso", "Peso");
        fieldNames.put("tipomotore", "Motore");
        fieldNames.put("cilindrata", "Cilindrata");
        fieldNames.put("potenza", "Potenza");
        fieldNames.put("coppia", "Coppia");
        fieldNames.put("velocità", "Velocità max");
        fieldNames.put("accelerazione", "Accelerazione 0-100");

        return fieldNames.getOrDefault(field.toLowerCase(), capitalizeFirst(field));
    }

    // Capitalizza prima lettera
    private String capitalizeFirst(String text) {
        if (text == null || text.isEmpty()) return text;
        return text.substring(0, 1).toUpperCase() + text.substring(1);
    }

    // Estrai URL immagine
    private String extractImageUrl(JsonObject json) {
        try {
            if (json.has("thumbnail") && json.getAsJsonObject("thumbnail").has("source")) {
                return json.getAsJsonObject("thumbnail").get("source").getAsString();
            }
            if (json.has("originalimage") && json.getAsJsonObject("originalimage").has("source")) {
                return json.getAsJsonObject("originalimage").get("source").getAsString();
            }
        } catch (Exception e) {
            System.err.println("Errore estrazione immagine: " + e.getMessage());
        }
        return null;
    }
}