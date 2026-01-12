import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.*;

import java.io.IOException;
import java.util.Random;

public class CarApiService {

    private static final String BASE_URL = "https://carapi.app/api";
    private static final String AUTH_URL = "https://carapi.app/api/auth/login";
    private final OkHttpClient client;
    private final Random random;
    private final String apiToken;
    private final String apiSecret;
    private String jwtToken;

    public CarApiService() {
        this.client = new OkHttpClient();
        this.random = new Random();
        this.apiToken = Config.get("CAR_API_TOKEN");
        this.apiSecret = Config.get("CAR_API_SECRET");

        if (this.apiToken == null || this.apiToken.trim().isEmpty() ||
                this.apiSecret == null || this.apiSecret.trim().isEmpty()) {
            throw new RuntimeException("CAR_API_TOKEN e CAR_API_SECRET devono essere configurati in config.properties");
        }

        // Autentica e ottieni il JWT token
        try {
            authenticate();
        } catch (IOException e) {
            throw new RuntimeException("Impossibile autenticarsi con CarAPI: " + e.getMessage(), e);
        }
    }

    private void authenticate() throws IOException {
        JsonObject authBody = new JsonObject();
        authBody.addProperty("api_token", apiToken);
        authBody.addProperty("api_secret", apiSecret);

        RequestBody body = RequestBody.create(
                authBody.toString(),
                MediaType.parse("application/json")
        );

        Request request = new Request.Builder()
                .url(AUTH_URL)
                .post(body)
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json")
                .build();

        System.out.println("Autenticazione con CarAPI...");

        try (Response response = client.newCall(request).execute()) {
            String responseBody = response.body().string();

            if (!response.isSuccessful()) {
                System.err.println("Errore autenticazione: " + responseBody);
                throw new IOException("Autenticazione fallita con codice " + response.code() + ": " + responseBody);
            }

            // La risposta è direttamente il JWT token come stringa
            // Rimuovi eventuali virgolette
            this.jwtToken = responseBody.replace("\"", "").trim();
            System.out.println("✓ Autenticazione completata con successo!");
            System.out.println("JWT Token ottenuto: " + jwtToken.substring(0, 20) + "...");
        }
    }

    private String makeRequest(String endpoint) throws IOException {
        String url = BASE_URL + endpoint;
        Request request = new Request.Builder()
                .url(url)
                .addHeader("Accept", "application/json")
                .addHeader("Authorization", "Bearer " + jwtToken)
                .build();

        System.out.println("Request URL: " + url);

        try (Response response = client.newCall(request).execute()) {
            String responseBody = response.body().string();

            if (!response.isSuccessful()) {
                System.err.println("Error response: " + responseBody); // Debug
                throw new IOException("Request failed with code " + response.code() + ": " + responseBody);
            }

            return responseBody;
        }
    }

    public String getMakes() {
        try {
            String json = makeRequest("/makes?limit=50");
            JsonObject jsonObject = JsonParser.parseString(json).getAsJsonObject();
            JsonArray data = jsonObject.getAsJsonArray("data");

            StringBuilder sb = new StringBuilder("🏭 Marche disponibili:\n\n");
            int count = 0;

            for (JsonElement element : data) {
                JsonObject make = element.getAsJsonObject();
                String name = make.get("name").getAsString();
                sb.append("• ").append(name).append("\n");
                count++;
                if (count >= 30) {
                    sb.append("\n... e molte altre!");
                    break;
                }
            }

            return sb.toString();
        } catch (Exception e) {
            return "❌ Errore nel recuperare le marche: " + e.getMessage();
        }
    }

    public String searchByMake(String makeName) {
        try {
            String json = makeRequest("/models/v2?make=" + makeName + "&limit=20");
            JsonObject jsonObject = JsonParser.parseString(json).getAsJsonObject();
            JsonArray data = jsonObject.getAsJsonArray("data");

            if (data.size() == 0) {
                return "❌ Nessun modello trovato per la marca: " + makeName;
            }

            StringBuilder sb = new StringBuilder("🚗 Modelli di " + makeName + ":\n\n");

            for (JsonElement element : data) {
                JsonObject model = element.getAsJsonObject();
                String name = model.get("name").getAsString();
                String make = model.get("make").getAsString();

                sb.append("📌 ").append(make).append(" ").append(name).append("\n");
                sb.append("   🔍 Per info: /dettagli ").append(make).append(" ").append(name).append("\n\n");
            }

            return sb.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return "❌ Errore nella ricerca: " + e.getMessage();
        }
    }

    public String getModelDetails(String modelInfo) {
        try {
            // Aspetta formato "Marca Modello" o "Marca Modello Anno"
            String[] parts = modelInfo.split(" ");
            if (parts.length < 2) {
                return "❌ Formato errato. Usa: /dettagli <marca> <modello>\nEsempio: /dettagli Toyota Camry";
            }

            String make = parts[0];
            String model = parts[1];
            String year = null;

            // Se c'è un terzo parametro, potrebbe essere l'anno
            if (parts.length >= 3) {
                String possibleYear = parts[2];
                if (possibleYear.matches("\\d{4}")) {
                    year = possibleYear;
                }
            }

            // Se non c'è l'anno, chiedi all'utente
            if (year == null) {
                // Prima ottieni gli anni disponibili
                String json = makeRequest("/models/v2?make=" + make + "&model=" + model + "&limit=50");
                JsonObject jsonObject = JsonParser.parseString(json).getAsJsonObject();
                JsonArray data = jsonObject.getAsJsonArray("data");

                if (data.size() == 0) {
                    return "❌ Nessun modello trovato per: " + make + " " + model;
                }

                // Raccogli gli anni disponibili
                StringBuilder sb = new StringBuilder("📅 " + make + " " + model + " - Scegli l'anno:\n\n");

                // Crea set per evitare duplicati
                java.util.Set<Integer> years = new java.util.TreeSet<>();
                for (JsonElement element : data) {
                    JsonObject m = element.getAsJsonObject();
                    if (m.has("year") && !m.get("year").isJsonNull()) {
                        years.add(m.get("year").getAsInt());
                    }
                }

                if (years.isEmpty()) {
                    // Se non ci sono anni, usa il primo risultato
                    JsonObject firstModel = data.get(0).getAsJsonObject();
                    int trimId = firstModel.get("id").getAsInt();
                    return getTrimDetails(String.valueOf(trimId));
                }

                // Mostra gli anni disponibili
                for (Integer y : years) {
                    sb.append("🔹 ").append(y).append(" - /dettagli ").append(make).append(" ").append(model).append(" ").append(y).append("\n");
                }

                return sb.toString();
            }

            // Se abbiamo l'anno, cerca il modello specifico
            String json = makeRequest("/models/v2?make=" + make + "&model=" + model + "&year=" + year + "&limit=1");
            JsonObject jsonObject = JsonParser.parseString(json).getAsJsonObject();
            JsonArray data = jsonObject.getAsJsonArray("data");

            if (data.size() == 0) {
                return "❌ Nessun modello trovato per: " + make + " " + model + " " + year;
            }

            // Prendi il primo ID e usa l'endpoint trims
            JsonObject firstModel = data.get(0).getAsJsonObject();
            int trimId = firstModel.get("id").getAsInt();

            // Ora ottieni i dettagli completi dall'endpoint trims
            return getTrimDetails(String.valueOf(trimId));

        } catch (Exception e) {
            e.printStackTrace();
            return "❌ Errore nel recuperare i dettagli: " + e.getMessage();
        }
    }

    private String getTrimDetails(String trimId) {
        try {
            String json = makeRequest("/trims/v2/" + trimId);

            // Debug: stampa la risposta
            System.out.println("Risposta trims API: " + json);

            JsonObject jsonResponse = JsonParser.parseString(json).getAsJsonObject();

            // Controlla se c'è un errore
            if (jsonResponse.has("exception")) {
                String error = jsonResponse.get("message").getAsString();
                return "❌ Errore API: " + error;
            }

            // Verifica che ci sia "data"
            if (!jsonResponse.has("data") || jsonResponse.get("data").isJsonNull()) {
                return "❌ Nessun dato disponibile per questo modello";
            }

            JsonObject data = jsonResponse.getAsJsonObject("data");

            StringBuilder sb = new StringBuilder("🚗 Dettagli Completi\n\n");

            // Informazioni base
            if (data.has("make") && !data.get("make").isJsonNull())
                sb.append("🏭 Marca: ").append(data.get("make").getAsString()).append("\n");
            if (data.has("model") && !data.get("model").isJsonNull())
                sb.append("🏷️ Modello: ").append(data.get("model").getAsString()).append("\n");
            if (data.has("year") && !data.get("year").isJsonNull())
                sb.append("📅 Anno: ").append(data.get("year").getAsInt()).append("\n");
            if (data.has("trim") && !data.get("trim").isJsonNull()) {
                sb.append("✨ Trim: ").append(data.get("trim").getAsString()).append("\n");
            }
            if (data.has("msrp") && !data.get("msrp").isJsonNull() && data.get("msrp").getAsInt() > 0) {
                sb.append("💰 MSRP: $").append(data.get("msrp").getAsInt()).append("\n");
            }

            // Bodies
            if (data.has("bodies") && data.get("bodies").isJsonArray()) {
                JsonArray bodies = data.getAsJsonArray("bodies");
                if (bodies.size() > 0) {
                    JsonObject body = bodies.get(0).getAsJsonObject();
                    sb.append("\n🔧 Carrozzeria:\n");

                    if (body.has("type") && !body.get("type").isJsonNull())
                        sb.append("  • Tipo: ").append(body.get("type").getAsString()).append("\n");
                    if (body.has("doors") && !body.get("doors").isJsonNull() && body.get("doors").getAsInt() > 0)
                        sb.append("  • Porte: ").append(body.get("doors").getAsInt()).append("\n");
                    if (body.has("seats") && !body.get("seats").isJsonNull() && body.get("seats").getAsInt() > 0)
                        sb.append("  • Posti: ").append(body.get("seats").getAsInt()).append("\n");
                    if (body.has("length") && !body.get("length").isJsonNull() && body.get("length").getAsDouble() > 0)
                        sb.append("  • Lunghezza: ").append(String.format("%.1f", body.get("length").getAsDouble())).append(" in\n");
                    if (body.has("width") && !body.get("width").isJsonNull() && body.get("width").getAsDouble() > 0)
                        sb.append("  • Larghezza: ").append(String.format("%.1f", body.get("width").getAsDouble())).append(" in\n");
                    if (body.has("height") && !body.get("height").isJsonNull() && body.get("height").getAsDouble() > 0)
                        sb.append("  • Altezza: ").append(String.format("%.1f", body.get("height").getAsDouble())).append(" in\n");
                    if (body.has("curb_weight") && !body.get("curb_weight").isJsonNull() && body.get("curb_weight").getAsInt() > 0)
                        sb.append("  • Peso: ").append(body.get("curb_weight").getAsInt()).append(" lbs\n");
                }
            }

            // Engines
            if (data.has("engines") && data.get("engines").isJsonArray()) {
                JsonArray engines = data.getAsJsonArray("engines");
                if (engines.size() > 0) {
                    JsonObject engine = engines.get(0).getAsJsonObject();
                    sb.append("\n⚙️ Motore:\n");

                    if (engine.has("engine_type") && !engine.get("engine_type").isJsonNull())
                        sb.append("  • Tipo: ").append(engine.get("engine_type").getAsString()).append("\n");
                    if (engine.has("cylinders") && !engine.get("cylinders").isJsonNull())
                        sb.append("  • Cilindri: ").append(engine.get("cylinders").getAsString()).append("\n");
                    if (engine.has("size") && !engine.get("size").isJsonNull() && engine.get("size").getAsDouble() > 0)
                        sb.append("  • Cilindrata: ").append(String.format("%.1f", engine.get("size").getAsDouble())).append(" L\n");
                    if (engine.has("horsepower_hp") && !engine.get("horsepower_hp").isJsonNull() && engine.get("horsepower_hp").getAsInt() > 0)
                        sb.append("  • Potenza: ").append(engine.get("horsepower_hp").getAsInt()).append(" HP\n");
                    if (engine.has("drive_type") && !engine.get("drive_type").isJsonNull())
                        sb.append("  • Trazione: ").append(engine.get("drive_type").getAsString()).append("\n");
                    if (engine.has("transmission") && !engine.get("transmission").isJsonNull())
                        sb.append("  • Trasmissione: ").append(engine.get("transmission").getAsString()).append("\n");
                }
            }

            return sb.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return "❌ Errore nel recuperare i dettagli: " + e.getMessage();
        }
    }

    public String searchByYear(String year) {
        try {
            String json = makeRequest("/models/v2?year=" + year + "&limit=20");
            JsonObject jsonObject = JsonParser.parseString(json).getAsJsonObject();
            JsonArray data = jsonObject.getAsJsonArray("data");

            if (data.size() == 0) {
                return "❌ Nessun modello trovato per l'anno: " + year;
            }

            StringBuilder sb = new StringBuilder("📅 Auto dell'anno " + year + ":\n\n");

            for (JsonElement element : data) {
                JsonObject model = element.getAsJsonObject();
                String name = model.get("name").getAsString();
                String make = model.get("make").getAsString();

                sb.append("🚗 ").append(make).append(" ").append(name).append("\n");
                sb.append("   🔍 Per info: /dettagli ").append(make).append(" ").append(name).append("\n\n");
            }

            return sb.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return "❌ Errore nella ricerca: " + e.getMessage();
        }
    }

    public String getRandomCar() {
        try {
            int randomPage = random.nextInt(50) + 1;
            String json = makeRequest("/models/v2?page=" + randomPage + "&limit=10");
            JsonObject jsonObject = JsonParser.parseString(json).getAsJsonObject();
            JsonArray data = jsonObject.getAsJsonArray("data");

            if (data.size() == 0) {
                return getRandomCar(); // Riprova
            }

            int randomIndex = random.nextInt(data.size());
            JsonObject model = data.get(randomIndex).getAsJsonObject();

            int id = model.get("id").getAsInt();
            return getTrimDetails(String.valueOf(id));
        } catch (Exception e) {
            e.printStackTrace();
            return "❌ Errore: " + e.getMessage();
        }
    }
}