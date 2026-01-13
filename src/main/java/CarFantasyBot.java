import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.ArrayList;
import java.util.List;

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

            // Gestisci comandi che richiedono immagini
            if (messageText.toLowerCase().startsWith("/cerca ")) {
                handleSearchWithPhoto(chatId, messageText);
            } else if (messageText.toLowerCase().startsWith("/dettagli ")) {
                handleDetailsWithPhoto(chatId, messageText);
            } else {
                String response = handleCommand(messageText);
                sendMessage(chatId, response);
            }
        }
    }

    private void handleSearchWithPhoto(long chatId, String command) {
        String[] parts = command.split(" ", 2);
        if (parts.length < 2) {
            sendMessage(chatId, "❌ Specifica una marca. Esempio: /cerca toyota");
            return;
        }
        String make = parts[1];
        SearchResult result = carApiService.searchByMakeWithImage(make);
        sendSearchResult(chatId, result);
    }

    private void handleDetailsWithPhoto(long chatId, String command) {
        String[] parts = command.split(" ", 2);
        if (parts.length < 2) {
            sendMessage(chatId, "❌ Specifica un modello. Esempio: /dettagli Toyota Camry");
            return;
        }
        String model = parts[1];
        SearchResult result = carApiService.getModelDetailsWithImage(model);
        sendDetailsWithButton(chatId, result, model);
    }

    private void sendSearchResult(long chatId, SearchResult result) {
        if (result.hasError()) {
            sendMessage(chatId, result.getErrorMessage());
            return;
        }

        if (result.getImageUrl() != null && !result.getImageUrl().isEmpty()) {
            sendPhoto(chatId, result.getImageUrl(), result.getCaption());
        } else {
            sendMessage(chatId, result.getCaption());
        }
    }

    private void sendDetailsWithButton(long chatId, SearchResult result, String model) {
        if (result.hasError()) {
            sendMessage(chatId, result.getErrorMessage());
            return;
        }

        // Se c'è un'immagine, invia con bottone
        if (result.getImageUrl() != null && !result.getImageUrl().isEmpty()) {
            sendPhotoWithButton(chatId, result.getImageUrl(), result.getCaption(), model);
        } else {
            // Se non c'è immagine, invia solo messaggio con bottone
            sendMessageWithButton(chatId, result.getCaption(), model);
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
                            "/cerca <marca> - Informazioni generali con foto\n" +
                            "/dettagli <modello> - Specifiche tecniche con foto\n" +
                            "/help - Mostra questo messaggio";

                case "/help":
                    return "🔍 Comandi disponibili:\n\n" +
                            "/cerca <marca> - Informazioni generali con foto (es: /cerca toyota)\n" +
                            "/dettagli <modello> - Specifiche tecniche con foto (es: /dettagli Toyota Camry)\n" +
                            "/help - Mostra questo messaggio";

                case "/cerca":
                    return parts.length < 2 ?
                            "❌ Specifica una marca. Esempio: /cerca toyota" :
                            carApiService.searchByMake(parts[1]);

                case "/dettagli":
                    return parts.length < 2 ?
                            "❌ Specifica un modello. Esempio: /dettagli Toyota Camry" :
                            carApiService.getModelDetails(parts[1]);

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
                .chatId(String.valueOf(chatId))
                .text(text)
                .parseMode("Markdown")
                .build();

        try {
            telegramClient.execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void sendMessageWithButton(long chatId, String text, String model) {
        // Crea il bottone inline usando InlineKeyboardRow
        List<InlineKeyboardRow> keyboard = new ArrayList<>();
        InlineKeyboardRow row = new InlineKeyboardRow();

        row.add(InlineKeyboardButton.builder()
                .text("⭐ Aggiungi ai preferiti")
                .callbackData("add_favorite_" + model.replace(" ", "_"))
                .build());

        keyboard.add(row);

        InlineKeyboardMarkup keyboardMarkup = InlineKeyboardMarkup.builder()
                .keyboard(keyboard)
                .build();

        SendMessage message = SendMessage
                .builder()
                .chatId(String.valueOf(chatId))
                .text(text)
                .parseMode("Markdown")
                .replyMarkup(keyboardMarkup)
                .build();

        try {
            telegramClient.execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void sendPhoto(long chatId, String photoUrl, String caption) {
        try {
            if (photoUrl == null || photoUrl.isEmpty()) {
                sendMessage(chatId, caption);
                return;
            }

            InputFile photo = new InputFile(photoUrl.trim());
            String safeCaption = caption.length() > 1024 ?
                    caption.substring(0, 1020) + "..." : caption;

            SendPhoto sendPhoto = SendPhoto
                    .builder()
                    .chatId(String.valueOf(chatId))
                    .photo(photo)
                    .caption(safeCaption)
                    .parseMode("Markdown")
                    .build();

            telegramClient.execute(sendPhoto);
        } catch (TelegramApiException e) {
            System.err.println("Errore Telegram: " + e.getMessage());
            // Fallback: invia solo il testo
            sendMessage(chatId, caption + "\n\n⚠️ *Impossibile caricare l'immagine*");
        } catch (Exception e) {
            System.err.println("Errore generale: " + e.getMessage());
            sendMessage(chatId, caption);
        }
    }

    private void sendPhotoWithButton(long chatId, String photoUrl, String caption, String model) {
        try {
            if (photoUrl == null || photoUrl.isEmpty()) {
                sendMessageWithButton(chatId, caption, model);
                return;
            }

            InputFile photo = new InputFile(photoUrl.trim());
            String safeCaption = caption.length() > 1024 ?
                    caption.substring(0, 1020) + "..." : caption;

            // Crea il bottone inline usando InlineKeyboardRow
            List<InlineKeyboardRow> keyboard = new ArrayList<>();
            InlineKeyboardRow row = new InlineKeyboardRow();

            row.add(InlineKeyboardButton.builder()
                    .text("⭐ Aggiungi ai preferiti")
                    .callbackData("add_favorite_" + model.replace(" ", "_"))
                    .build());

            keyboard.add(row);

            InlineKeyboardMarkup keyboardMarkup = InlineKeyboardMarkup.builder()
                    .keyboard(keyboard)
                    .build();

            SendPhoto sendPhoto = SendPhoto
                    .builder()
                    .chatId(String.valueOf(chatId))
                    .photo(photo)
                    .caption(safeCaption)
                    .parseMode("Markdown")
                    .replyMarkup(keyboardMarkup)
                    .build();

            telegramClient.execute(sendPhoto);
        } catch (TelegramApiException e) {
            System.err.println("Errore Telegram inviando foto con bottone: " + e.getMessage());
            // Fallback: invia messaggio con bottone
            sendMessageWithButton(chatId, caption, model);
        } catch (Exception e) {
            System.err.println("Errore generale: " + e.getMessage());
            sendMessage(chatId, caption);
        }
    }
}