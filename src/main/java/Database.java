import java.sql.*;

public class Database {

    private static final String DB_URL = "jdbc:sqlite:database/CarbotDatabase";
    private static Database instance;
    private Connection connection;

    private Database(){openConnection();}

    public static Database getInstance(){
        if(instance == null){
            instance = new Database();
        }
        return instance;
    }

    private void openConnection(){
        try{
            if(connection == null || connection.isClosed()){
                System.out.println("Riapertura connessione");
                openConnection();
            }
        }
        catch (SQLException e){
            throw new RuntimeException("Errore durante la connesisone");
        }
    }

}
