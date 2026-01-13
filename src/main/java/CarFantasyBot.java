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
        // Gestisci callback dei bottoni
        if (update.hasCallbackQuery()) {
            handleCallbackQuery(update);
            return;
        }

        if (update.hasMessage() && update.getMessage().hasText()) {
            String messageText = update.getMessage().getText();
            long chatId = update.getMessage().getChatId();

            // Salva/aggiorna utente nel database
            var message = update.getMessage();
            var user = message.getFrom();
            if (user != null) {
                Database.getInstance().addOrUpdateUser(
                        user.getId(),
                        user.getUserName(),
                        user.getFirstName(),
                        user.getLastName()
                );
            }

            // Gestisci comandi che richiedono immagini
            if (messageText.toLowerCase().startsWith("/cerca ")) {
                handleSearchWithPhoto(chatId, messageText);
            } else if (messageText.toLowerCase().startsWith("/dettagli ")) {
                handleDetailsWithPhoto(chatId, messageText);
            } else if (messageText.toLowerCase().startsWith("/preferiti")) {
                handleFavorites(chatId);
            } else {
                String response = handleCommand(messageText);
                sendMessage(chatId, response, true); // Con Markdown per comandi base
            }
        }
    }

    // Gestisci i callback dei bottoni
    private void handleCallbackQuery(Update update) {
        var callbackQuery = update.getCallbackQuery();
        String callbackData = callbackQuery.getData();
        long chatId = callbackQuery.getMessage().getChatId();
        long userId = callbackQuery.getFrom().getId();
        String username = callbackQuery.getFrom().getUserName();
        String firstName = callbackQuery.getFrom().getFirstName();
        String lastName = callbackQuery.getFrom().getLastName();

        // Salva utente
        Database.getInstance().addOrUpdateUser(userId, username, firstName, lastName);

        if (callbackData.startsWith("add_favorite_")) {
            String carName = callbackData.replace("add_favorite_", "").replace("_", " ");

            boolean added = Database.getInstance().addFavorite(userId, carName);

            String responseText;
            if (added) {
                responseText = "⭐ " + carName + " aggiunto ai preferiti!\n\n" +
                        "Usa /preferiti per vedere la tua lista.";
            } else {
                responseText = "ℹ️ " + carName + " è già nei tuoi preferiti!";
            }

            // Rispondi al callback
            answerCallbackQuery(callbackQuery.getId(), responseText);

        } else if (callbackData.startsWith("remove_favorite_")) {
            String carName = callbackData.replace("remove_favorite_", "").replace("_", " ");

            boolean removed = Database.getInstance().removeFavorite(userId, carName);

            String responseText = removed ?
                    "🗑️ " + carName + " rimosso dai preferiti" :
                    "❌ Errore nella rimozione";

            answerCallbackQuery(callbackQuery.getId(), responseText);

            // Aggiorna la lista preferiti
            handleFavorites(chatId);
        }
    }

    // Mostra i preferiti dell'utente
    private void handleFavorites(long chatId) {
        List<String> favorites = Database.getInstance().getUserFavorites(chatId);

        if (favorites.isEmpty()) {
            sendMessage(chatId, "⭐ Non hai ancora preferiti!\n\n" +
                    "Usa /dettagli <modello> e clicca sul bottone per aggiungere auto ai preferiti.", true);
            return;
        }

        StringBuilder message = new StringBuilder("⭐ I TUOI PREFERITI (" + favorites.size() + ")\n\n");

        // Crea bottoni per rimuovere
        List<InlineKeyboardRow> keyboard = new ArrayList<>();

        for (String car : favorites) {
            message.append("🚗 ").append(car).append("\n");

            InlineKeyboardRow row = new InlineKeyboardRow();
            row.add(InlineKeyboardButton.builder()
                    .text("🗑️ " + car)
                    .callbackData("remove_favorite_" + car.replace(" ", "_"))
                    .build());
            keyboard.add(row);
        }

        message.append("\n💡 Clicca su un bottone per rimuovere un'auto dai preferiti.");

        InlineKeyboardMarkup keyboardMarkup = InlineKeyboardMarkup.builder()
                .keyboard(keyboard)
                .build();

        SendMessage sendMessage = SendMessage.builder()
                .chatId(String.valueOf(chatId))
                .text(message.toString())
                .replyMarkup(keyboardMarkup)
                .build();

        try {
            telegramClient.execute(sendMessage);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    // Rispondi a un callback query
    private void answerCallbackQuery(String callbackQueryId, String text) {
        try {
            telegramClient.execute(org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery.builder()
                    .callbackQueryId(callbackQueryId)
                    .text(text)
                    .showAlert(false)
                    .build());
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void handleSearchWithPhoto(long chatId, String command) {
        String[] parts = command.split(" ", 2);
        if (parts.length < 2) {
            sendMessage(chatId, "❌ Specifica una marca. Esempio: /cerca toyota", true);
            return;
        }
        String make = parts[1];
        SearchResult result = carApiService.searchByMakeWithImage(make);
        sendSearchResult(chatId, result);
    }

    private void handleDetailsWithPhoto(long chatId, String command) {
        String[] parts = command.split(" ", 2);
        if (parts.length < 2) {
            sendMessage(chatId, "❌ Specifica un modello. Esempio: /dettagli Toyota Camry", true);
            return;
        }
        String model = parts[1];
        SearchResult result = carApiService.getModelDetailsWithImage(model);
        sendDetailsWithButton(chatId, result, model);
    }

    private void sendSearchResult(long chatId, SearchResult result) {
        if (result.hasError()) {
            sendMessage(chatId, result.getErrorMessage(), true);
            return;
        }

        if (result.getImageUrl() != null && !result.getImageUrl().isEmpty()) {
            sendPhoto(chatId, result.getImageUrl(), result.getCaption(), true); // Con Markdown
        } else {
            sendMessage(chatId, result.getCaption(), true);
        }
    }

    private void sendDetailsWithButton(long chatId, SearchResult result, String model) {
        if (result.hasError()) {
            sendMessage(chatId, result.getErrorMessage(), false);
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
                            "/preferiti - Mostra i tuoi preferiti\n" +
                            "/help - Mostra questo messaggio";

                case "/help":
                    return "📖 Comandi disponibili:\n\n" +
                            "/cerca <marca> - Informazioni generali con foto (es: /cerca toyota)\n" +
                            "/dettagli <modello> - Specifiche tecniche con foto (es: /dettagli Ferrari F40)\n" +
                            "/preferiti - Mostra la tua lista di auto preferite\n" +
                            "/help - Mostra questo messaggio";

                case "/preferiti":
                    return "Usa il comando /preferiti per vedere la tua lista!";

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

    private void sendMessage(long chatId, String text, boolean useMarkdown) {
        SendMessage.SendMessageBuilder builder = SendMessage
                .builder()
                .chatId(String.valueOf(chatId))
                .text(text);

        if (useMarkdown) {
            builder.parseMode("Markdown");
        }

        SendMessage message = builder.build();

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
                // NON usiamo parseMode per i dettagli tecnici
                .replyMarkup(keyboardMarkup)
                .build();

        try {
            telegramClient.execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void sendPhoto(long chatId, String photoUrl, String caption, boolean useMarkdown) {
        try {
            if (photoUrl == null || photoUrl.isEmpty()) {
                sendMessage(chatId, caption, useMarkdown);
                return;
            }

            InputFile photo = new InputFile(photoUrl.trim());
            String safeCaption = caption.length() > 1024 ?
                    caption.substring(0, 1020) + "..." : caption;

            SendPhoto.SendPhotoBuilder builder = SendPhoto
                    .builder()
                    .chatId(String.valueOf(chatId))
                    .photo(photo)
                    .caption(safeCaption);

            if (useMarkdown) {
                builder.parseMode("Markdown");
            }

            SendPhoto sendPhoto = builder.build();

            telegramClient.execute(sendPhoto);
        } catch (TelegramApiException e) {
            System.err.println("Errore Telegram: " + e.getMessage());
            // Fallback: invia solo il testo
            sendMessage(chatId, caption + "\n\n⚠️ Impossibile caricare l'immagine", useMarkdown);
        } catch (Exception e) {
            System.err.println("Errore generale: " + e.getMessage());
            sendMessage(chatId, caption, useMarkdown);
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
                    // NON usiamo parseMode per i dettagli tecnici
                    .replyMarkup(keyboardMarkup)
                    .build();

            telegramClient.execute(sendPhoto);
        } catch (TelegramApiException e) {
            System.err.println("Errore Telegram inviando foto con bottone: " + e.getMessage());
            // Fallback: invia messaggio con bottone
            sendMessageWithButton(chatId, caption, model);
        } catch (Exception e) {
            System.err.println("Errore generale: " + e.getMessage());
            sendMessage(chatId, caption, false);
        }
    }
}