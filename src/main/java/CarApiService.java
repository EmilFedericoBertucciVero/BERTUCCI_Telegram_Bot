import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.util.Random;

public class CarApiService {

    private static final String BASE_URL = "https://carapi.app/api";
    private final OkHttpClient client;
    private final Random random;

    public CarApiService() {
        this.client = new OkHttpClient();
        this.random = new Random();
    }

    private String makeRequest(String endpoint) throws IOException {
        Request request = new Request.Builder()
                .url(BASE_URL + endpoint)
                .addHeader("Accept", "application/json")
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Request failed: " + response);
            }
            return response.body().string();
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
            String json = makeRequest("/models?make=" + makeName + "&limit=20");
            JsonObject jsonObject = JsonParser.parseString(json).getAsJsonObject();
            JsonArray data = jsonObject.getAsJsonArray("data");

            if (data.size() == 0) {
                return "❌ Nessun modello trovato per la marca: " + makeName;
            }

            StringBuilder sb = new StringBuilder("🚗 Modelli di " + makeName + ":\n\n");

            for (JsonElement element : data) {
                JsonObject model = element.getAsJsonObject();
                int id = model.get("id").getAsInt();
                String name = model.get("name").getAsString();
                int year = model.get("year").getAsInt();

                sb.append("📌 ").append(name).append(" (").append(year).append(")\n");
                sb.append("   ID: ").append(id).append("\n\n");
            }

            sb.append("💡 Usa /modello <id> per dettagli");
            return sb.toString();
        } catch (Exception e) {
            return "❌ Errore nella ricerca: " + e.getMessage();
        }
    }

    public String getModelDetails(String modelId) {
        try {
            String json = makeRequest("/models/" + modelId);
            JsonObject data = JsonParser.parseString(json).getAsJsonObject().getAsJsonObject("data");

            StringBuilder sb = new StringBuilder("🚗 Dettagli Auto\n\n");
            sb.append("🏷️ Modello: ").append(data.get("name").getAsString()).append("\n");
            sb.append("🏭 Marca: ").append(data.get("make_model").getAsJsonObject().get("name").getAsString()).append("\n");
            sb.append("📅 Anno: ").append(data.get("year").getAsInt()).append("\n");

            if (data.has("transmission") && !data.get("transmission").isJsonNull()) {
                sb.append("⚙️ Trasmissione: ").append(data.get("transmission").getAsString()).append("\n");
            }

            if (data.has("drive") && !data.get("drive").isJsonNull()) {
                sb.append("🔧 Trazione: ").append(data.get("drive").getAsString()).append("\n");
            }

            if (data.has("fuel_type") && !data.get("fuel_type").isJsonNull()) {
                sb.append("⛽ Carburante: ").append(data.get("fuel_type").getAsString()).append("\n");
            }

            if (data.has("horsepower") && !data.get("horsepower").isJsonNull()) {
                sb.append("🏎️ Cavalli: ").append(data.get("horsepower").getAsInt()).append(" HP\n");
            }

            if (data.has("cylinders") && !data.get("cylinders").isJsonNull()) {
                sb.append("🔩 Cilindri: ").append(data.get("cylinders").getAsInt()).append("\n");
            }

            return sb.toString();
        } catch (Exception e) {
            return "❌ Errore nel recuperare i dettagli: " + e.getMessage();
        }
    }

    public String searchByYear(String year) {
        try {
            String json = makeRequest("/models?year=" + year + "&limit=15");
            JsonObject jsonObject = JsonParser.parseString(json).getAsJsonObject();
            JsonArray data = jsonObject.getAsJsonArray("data");

            if (data.size() == 0) {
                return "❌ Nessun modello trovato per l'anno: " + year;
            }

            StringBuilder sb = new StringBuilder("📅 Auto dell'anno " + year + ":\n\n");

            for (JsonElement element : data) {
                JsonObject model = element.getAsJsonObject();
                int id = model.get("id").getAsInt();
                String name = model.get("name").getAsString();
                String make = model.get("make_model").getAsJsonObject().get("name").getAsString();

                sb.append("🚗 ").append(make).append(" ").append(name).append("\n");
                sb.append("   ID: ").append(id).append("\n\n");
            }

            sb.append("💡 Usa /modello <id> per dettagli");
            return sb.toString();
        } catch (Exception e) {
            return "❌ Errore nella ricerca: " + e.getMessage();
        }
    }

    public String getRandomCar() {
        try {
            int randomPage = random.nextInt(50) + 1;
            String json = makeRequest("/models?page=" + randomPage + "&limit=10");
            JsonObject jsonObject = JsonParser.parseString(json).getAsJsonObject();
            JsonArray data = jsonObject.getAsJsonArray("data");

            if (data.size() == 0) {
                return getRandomCar(); // Riprova
            }

            int randomIndex = random.nextInt(data.size());
            JsonObject model = data.get(randomIndex).getAsJsonObject();

            int id = model.get("id").getAsInt();
            return getModelDetails(String.valueOf(id));
        } catch (Exception e) {
            return "❌ Errore: " + e.getMessage();
        }
    }
}
