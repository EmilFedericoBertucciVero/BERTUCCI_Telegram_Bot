import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;

public class Main {

    public static void main(String[] args) {
        String botToken = Config.get("BOT_TOKEN");
        try (TelegramBotsLongPollingApplication botsApplication = new TelegramBotsLongPollingApplication()) {
            botsApplication.registerBot(botToken, new CarFantasyBot(botToken));
            System.out.println("CarFantasyBot avviato con successo!");
            Thread.currentThread().join();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
