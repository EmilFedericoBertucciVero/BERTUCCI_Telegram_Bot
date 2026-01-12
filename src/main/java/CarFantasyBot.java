import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

public class CarFantasyBot implements LongPollingSingleThreadUpdateConsumer {

    private final TelegramClient telegramClient;
    private final CarApiService carApiService;

    public CarFantasyBot(String botToken) {
        this.telegramClient = new OkHttpTelegramClient(botToken);
        this.carApiService = new CarApiService();
    }

    @Override
    public void consume(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            String messageText = update.getMessage().getText();
            long chatId = update.getMessage().getChatId();

            String response = handleCommand(messageText);
            sendMessage(chatId, response);
        }
    }

    private String handleCommand(String command) {
        String[] parts = command.split(" ", 2);
        String cmd = parts[0].toLowerCase();

        try {
            switch (cmd) {
                case "/start":
                    return "🚗 Benvenuto nel Car Fantasy Bot!\n\n" +
                            "Comandi disponibili:\n" +
                            "/marche - Lista di tutte le marche disponibili\n" +
                            "/cerca <marca> - Cerca modelli per marca (es: /cerca toyota)\n" +
                            "/dettagli <marca> <modello> - Info complete (es: /dettagli Toyota Camry)\n" +
                            "  Puoi anche specificare l'anno: /dettagli Toyota Camry 2020\n" +
                            "/anno <anno> - Cerca auto per anno (es: /anno 2020)\n" +
                            "/random - Mostra una macchina casuale\n" +
                            "/help - Mostra questo messaggio";

                case "/help":
                    return "🔍 Comandi disponibili:\n\n" +
                            "/marche - Mostra tutte le marche\n" +
                            "/cerca <marca> - Cerca modelli (es: /cerca bmw)\n" +
                            "/dettagli <marca> <modello> [anno] - Info complete\n" +
                            "  Esempi:\n" +
                            "  /dettagli BMW M3 (mostra anni disponibili)\n" +
                            "  /dettagli BMW M3 2020 (dettagli anno specifico)\n" +
                            "/anno <anno> - Cerca per anno (es: /anno 2021)\n" +
                            "/random - Mostra una macchina casuale";

                case "/marche":
                    return carApiService.getMakes();

                case "/cerca":
                    if (parts.length < 2) {
                        return "❌ Specifica una marca. Esempio: /cerca toyota";
                    }
                    return carApiService.searchByMake(parts[1]);

                case "/dettagli":
                    if (parts.length < 2) {
                        return "❌ Specifica marca e modello. Esempio: /dettagli Toyota Camry";
                    }
                    return carApiService.getModelDetails(parts[1]);

                case "/anno":
                    if (parts.length < 2) {
                        return "❌ Specifica un anno. Esempio: /anno 2020";
                    }
                    return carApiService.searchByYear(parts[1]);

                case "/random":
                    return carApiService.getRandomCar();

                default:
                    return "❌ Comando non riconosciuto. Usa /help per vedere i comandi disponibili.";
            }
        } catch (Exception e) {
            return "❌ Errore: " + e.getMessage();
        }
    }

    private void sendMessage(long chatId, String text) {
        SendMessage message = SendMessage
                .builder()
                .chatId(chatId)
                .text(text)
                .build();

        try {
            telegramClient.execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }
}