import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class Database {

    private static final String DB_URL = "jdbc:sqlite:Databases/CarbotDatabase.db";
    private static Database instance;
    private Connection connection;

    private Database() {
        openConnection();
        createTables();
    }

    public static Database getInstance() {
        if (instance == null) {
            instance = new Database();
        }
        return instance;
    }

    private void openConnection() {
        try {
            if (connection == null || connection.isClosed()) {
                System.out.println("Apertura connessione al database...");
                connection = DriverManager.getConnection(DB_URL);
                System.out.println("✓ Connessione stabilita");
            }
        } catch (SQLException e) {
            throw new RuntimeException("Errore durante la connessione: " + e.getMessage());
        }
    }

    private void createTables() {
        String createUsersTable = """
            CREATE TABLE IF NOT EXISTS Users (
                UserId INTEGER PRIMARY KEY,
                Username TEXT,
                FirstName TEXT,
                LastName TEXT,
                CreatedAt TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
        """;

        String createFavoritesTable = """
            CREATE TABLE IF NOT EXISTS Favorites (
                FavoriteId INTEGER PRIMARY KEY AUTOINCREMENT,
                UserId INTEGER NOT NULL,
                CarName TEXT NOT NULL,
                AddedAt TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY (UserId) REFERENCES Users(UserId),
                UNIQUE(UserId, CarName)
            )
        """;

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createUsersTable);
            stmt.execute(createFavoritesTable);
            System.out.println("✓ Tabelle create/verificate");
        } catch (SQLException e) {
            System.err.println("Errore creazione tabelle: " + e.getMessage());
        }
    }

    // Aggiungi o aggiorna un utente
    public void addOrUpdateUser(long userId, String username, String firstName, String lastName) {
        String sql = """
            INSERT INTO Users (UserId, Username, FirstName, LastName)
            VALUES (?, ?, ?, ?)
            ON CONFLICT(UserId) DO UPDATE SET
                Username = excluded.Username,
                FirstName = excluded.FirstName,
                LastName = excluded.LastName
        """;

        try {
            ensureConnection();
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setLong(1, userId);
                pstmt.setString(2, username);
                pstmt.setString(3, firstName);
                pstmt.setString(4, lastName);
                pstmt.executeUpdate();
                System.out.println("✓ Utente salvato: " + userId);
            }
        } catch (SQLException e) {
            System.err.println("Errore salvataggio utente: " + e.getMessage());
        }
    }

    // Aggiungi un'auto ai preferiti
    public boolean addFavorite(long userId, String carName) {
        String sql = "INSERT INTO Favorites (UserId, CarName) VALUES (?, ?)";

        try {
            ensureConnection();
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setLong(1, userId);
                pstmt.setString(2, carName);
                pstmt.executeUpdate();
                System.out.println("✓ Preferito aggiunto: " + carName + " per utente " + userId);
                return true;
            }
        } catch (SQLException e) {
            // Errore UNIQUE constraint = già nei preferiti
            if (e.getMessage().contains("UNIQUE")) {
                System.out.println("ℹ Auto già nei preferiti");
                return false;
            }
            System.err.println("Errore aggiunta preferito: " + e.getMessage());
            return false;
        }
    }

    // Rimuovi un'auto dai preferiti
    public boolean removeFavorite(long userId, String carName) {
        String sql = "DELETE FROM Favorites WHERE UserId = ? AND CarName = ?";

        try {
            ensureConnection();
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setLong(1, userId);
                pstmt.setString(2, carName);
                int deleted = pstmt.executeUpdate();
                System.out.println("✓ Preferito rimosso: " + carName);
                return deleted > 0;
            }
        } catch (SQLException e) {
            System.err.println("Errore rimozione preferito: " + e.getMessage());
            return false;
        }
    }

    // Verifica se un'auto è nei preferiti
    public boolean isFavorite(long userId, String carName) {
        String sql = "SELECT COUNT(*) FROM Favorites WHERE UserId = ? AND CarName = ?";

        try {
            ensureConnection();
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setLong(1, userId);
                pstmt.setString(2, carName);
                try (ResultSet rs = pstmt.executeQuery()) {
                    return rs.next() && rs.getInt(1) > 0;
                }
            }
        } catch (SQLException e) {
            System.err.println("Errore verifica preferito: " + e.getMessage());
            return false;
        }
    }

    // Ottieni tutti i preferiti di un utente
    public List<String> getUserFavorites(long userId) {
        List<String> favorites = new ArrayList<>();
        String sql = "SELECT CarName FROM Favorites WHERE UserId = ? ORDER BY AddedAt DESC";

        try {
            ensureConnection();
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setLong(1, userId);
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        favorites.add(rs.getString("CarName"));
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Errore recupero preferiti: " + e.getMessage());
        }

        return favorites;
    }

    // Conta i preferiti di un utente
    public int getFavoritesCount(long userId) {
        String sql = "SELECT COUNT(*) FROM Favorites WHERE UserId = ?";

        try {
            ensureConnection();
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setLong(1, userId);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt(1);
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Errore conteggio preferiti: " + e.getMessage());
        }

        return 0;
    }

    // Assicura che la connessione sia aperta
    private void ensureConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            openConnection();
        }
    }

    // Chiudi la connessione
    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                System.out.println("✓ Connessione chiusa");
            }
        } catch (SQLException e) {
            System.err.println("Errore chiusura connessione: " + e.getMessage());
        }
    }
}