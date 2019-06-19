package lab.server;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;

public class Server {
    private static String outLogFile = "out.log";
    private static String errLogFile = "err.log";
    private static int port = 8080;
    private static ServerSocket serverSocket;
    private static Logger logger;
    private static DataBaseConnection db;

    public static void main(String[] args) {

        if (args.length>0){
            try{port = Integer.parseInt(args[0]);}
            catch(NumberFormatException e){e.getLocalizedMessage();}
        }

        try {
            System.setOut(new PrintStream(System.out, true, "UTF-8"));
        } catch (UnsupportedEncodingException ignored) {}

        initLogger();

        try {
            serverSocket = new ServerSocket(port);
            db = new DataBaseConnection();
            logger.log("Сервер запущен и слушает порт " + port + "...");
        } catch (IOException e) {
            logger.err("Ошибка создания серверного сокета (" + e.getLocalizedMessage() + "), приложение будет остановлено.");
            System.exit(1);
        }

        initTables();
        Wardrobe wardrobe = new Wardrobe();
        db.loadHats(wardrobe);


        while (true) {
            try {
                Socket clientSocket = serverSocket.accept();
                new Thread(new RequestResolver(clientSocket, wardrobe, logger, db)).start();
            } catch (IOException e) {
                logger.err("Ошибка подключения: " + e.getMessage());
            }
        }
    }

    private static void initTables() {
        logger.log("Проверка таблиц...");

        autoCreateTable(
                "hats",
                "id serial primary key not null, size integer, color text, num integer, contents text, createdDate text, username text"
        );

        autoCreateTable(
                "users",
                "id serial primary key not null, username text, email text unique, password_hash text"
        );


    }
    private static void autoCreateTable(String name, String structure) {
        try {
            DatabaseMetaData metaData = db.getConnection().getMetaData();
            if (
                    !metaData.getTables(
                            null,
                            null,
                             name,
                            new String[]{"TABLE"}
                    ).next()
            ) {
                db.getConnection().createStatement().execute("create table if not exists " +  name +" (" + structure + ")");
                logger.log("Создана таблица " + name);
            }
        } catch (SQLException e) {
            logger.err("Не получилось создать таблицу " + name + ": " + e.getMessage());
        }
    }

    /**
     * Инициализирует логгер для сервера
     */
    private static void initLogger() {
        try {
            logger = new Logger(
                    new PrintStream(new TeeOutputStream(System.out, new FileOutputStream(outLogFile)), true, "UTF-8"),
                    new PrintStream(new TeeOutputStream(System.err, new FileOutputStream(errLogFile)), true, "UTF-8")
            );
        } catch (IOException e) {
            System.err.println("Ошибка записи логов: " + e.getLocalizedMessage());
            System.exit(1);
        }
    }

}