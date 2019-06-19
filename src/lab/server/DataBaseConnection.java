package lab.server;

import lab.Hat;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.*;
import java.time.ZonedDateTime;
import java.util.Base64;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicInteger;


class DataBaseConnection {
    private final static byte[] SALT = "HF2Ddf3s436".getBytes();
    private Connection connection = null;
    private AtomicInteger hatindex = new AtomicInteger(Math.round((ZonedDateTime.now()).getNano()));
    private AtomicInteger userindex = new AtomicInteger(Math.round((ZonedDateTime.now()).getNano()));


    {

        try {
            Class.forName("org.postgresql.Driver");
            System.out.println("Installed Driver");
            String url = "jdbc:postgresql://localhost:5432/studs";
            String name = "lesti";
            String pass = "rbh.ifkfgf";
            connection = DriverManager.getConnection(url, name, pass);
            System.out.println("Успешное подключение\n");
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Не удалось подключиться к базе данных");
        }
    }

    Connection getConnection() {
        return this.connection;
    }


    void loadHats(Wardrobe ward) {
        try {
            ZonedDateTime time = ZonedDateTime.now();
            PreparedStatement preStatement = connection.prepareStatement("SELECT * FROM hats;");
            ResultSet result = preStatement.executeQuery();
            while (result.next()) {
                String username = result.getString("username");
                int size = result.getInt("size");
                String color = result.getString("color");
                int num = result.getInt("num");
                String date = result.getString("createddate");
                if (date != null) {
                    time = ZonedDateTime.parse(result.getString("createddate"));
                }
                Hat h = new Hat(size,color,num);
                h.setCreatedDate(time);
                h.setUser(username);
                ward.add(h);

            }
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Ошибка при загрузке гардероба");
        }
    }

    void addToDB(Hat h, String username) {
        try {
            addHat(h, username);
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Ошибка при добавлении шляпы в базу данных");
        }
    }

    private void addHat(Hat h, String username) throws SQLException {
        hatindex.incrementAndGet();
        PreparedStatement preStatement = connection.prepareStatement("INSERT INTO hats VALUES (?, ?, ?, ?, ?, ?,?);");
        preStatement.setInt(1, new Integer(String.valueOf(hatindex)));
        preStatement.setInt(2, h.getSize());
        preStatement.setString(3, h.getColor());
        preStatement.setInt(4, h.getNum());
        preStatement.setString(5, h.contentlist());
        preStatement.setString(6, h.getCreatedDate().toString());
        preStatement.setString(7, username);
        preStatement.executeUpdate();
    }

    void clear() throws SQLException {
        PreparedStatement preStatement = connection.prepareStatement("DELETE FROM hats;");
        preStatement.executeUpdate();
    }

    void saveHats(Wardrobe hats, String filename) {
        try {
            if (hats != null) {

                Iterator<Hat> iterator = hats.iterator();

                while (iterator.hasNext()) {
                    //Hat h = iterator.next();
                    WardrobeLoaderSaver.save(hats, filename);
                }
                System.out.println("База данных была обновлена");
            } else {
                System.out.println("Гардероб пуст.");
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Ошибка при сохранении в базу данных");
        }
    }

    void removeHat(Hat hat, String username) {

        if (username.equals(hat.getUser())) {
            try {
        PreparedStatement preStatement = connection.prepareStatement("DELETE FROM hats WHERE size=? AND color=? AND num=? AND username=?;");
        preStatement.setInt(1, hat.getSize());
        preStatement.setString(2,hat.getColor());
        preStatement.setInt(3,hat.getNum());
        preStatement.setString(4,username);
        preStatement.executeUpdate();
            } catch (Exception e) {
                System.out.println("Ошибка при удалении из базы данных");
            }
        }
    }

    int executeLogin(String login, String pass) {
        try {
            PreparedStatement preStatement = connection.prepareStatement("SELECT * FROM users WHERE username=? and password_hash=?;");
            String hash = DataBaseConnection.computeSaltedBase64Hash(pass, SALT, "SHA-512", "");
            preStatement.setString(1,login);
            preStatement.setString(2,hash);
            ResultSet result = preStatement.executeQuery();
            if (result.next()) return 0;
            else {
                PreparedStatement preStatement2 = connection.prepareStatement("SELECT * FROM users WHERE username=?;");
                preStatement2.setString(1,login);
                ResultSet result2 = preStatement2.executeQuery();
                if (result2.next()) return 2;
                else return 1;
            }
        } catch (Exception e) {
            System.out.println("Ошибка при входе");
            return -1;
        }
    }

    int executeRegister(String login, String mail, String pass) {
        userindex.incrementAndGet();

        try {


            PreparedStatement ifLog = connection.prepareStatement("SELECT * FROM users WHERE username=?;");

            ifLog.setString(1,login);
            ResultSet result = ifLog.executeQuery();
            if (result.next()){return 0;}
            String hash = DataBaseConnection.computeSaltedBase64Hash(pass, SALT, "SHA-512", "");
            PreparedStatement statement = connection.prepareStatement("INSERT INTO users VALUES (?, ?, ?,?);");
            statement.setInt(1, new Integer(String.valueOf(userindex)));
            statement.setString(2, login);
            statement.setString(3, mail);
            statement.setString(4, hash);
            statement.executeUpdate();
            new Thread(() -> JavaMail.registration(mail, pass)).start();
            return 1;
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Error whilst registration");
            return -1;
        }
    }

//    public static boolean isHashMatch(String password, // the password you want to check.
//                                      String saltedHash, // the salted hash you want to check your password against.
//                                      String hashAlgorithm, // the algorithm you want to use.
//                                      String delimiter) throws NoSuchAlgorithmException // the delimiter that has been used to delimit the salt and the hash.
//    {
//        // get the salt from the salted hash and decode it into a byte[].
//        byte[] salt = Base64.getDecoder()
//                .decode(saltedHash.split(delimiter)[0]);
//        // compute a new salted hash based on the provided password and salt.
//        String pw_saltedHash = computeSaltedBase64Hash(password,
//                salt,
//                hashAlgorithm,
//                delimiter);
//        // check if the provided salted hash matches the salted hash we computed from the password and salt.
//        return saltedHash.equals(pw_saltedHash);
//    }
//
//    public static String computeSaltedBase64Hash(String password, // the password you want to hash
//                                                 String hashAlgorithm, // the algorithm you want to use.
//                                                 String delimiter) throws NoSuchAlgorithmException // the delimiter that will be used to delimit the salt and the hash.
//    {
//        // compute the salted hash with a random salt.
//        return computeSaltedBase64Hash(password, null, hashAlgorithm, delimiter);
//    }

    private static String computeSaltedBase64Hash(String password, // the password you want to hash
                                                  byte[] salt, // the salt you want to use (uses random salt if null).
                                                  String hashAlgorithm, // the algorithm you want to use.
                                                  String delimiter) throws NoSuchAlgorithmException // the delimiter that will be used to delimit the salt and the hash.
    {
        // transform the password string into a byte[]. we have to do this to work with it later.
        byte[] passwordBytes = password.getBytes();
        byte[] saltBytes;

        if (salt != null) {
            saltBytes = salt;
        } else {
            // if null has been provided as salt parameter create a new random salt.
            saltBytes = new byte[64];
            SecureRandom secureRandom = new SecureRandom();
            secureRandom.nextBytes(saltBytes);
        }

        // MessageDigest converts our password and salt into a hash.
        MessageDigest messageDigest = MessageDigest.getInstance(hashAlgorithm);
        // concatenate the salt byte[] and the password byte[].
        byte[] saltAndPassword = concatArrays(saltBytes, passwordBytes);
        // create the hash from our concatenated byte[].
        byte[] saltedHash = messageDigest.digest(saltAndPassword);
        // get java's base64 encoder for encoding.
        Base64.Encoder base64Encoder = Base64.getEncoder();
        // create a StringBuilder to build the result.
        StringBuilder result = new StringBuilder();

        result.append(base64Encoder.encodeToString(saltBytes)) // base64-encode the salt and append it.
                .append(delimiter) // append the delimiter (watch out! don't use regex expressions as delimiter if you plan to use String.split() to isolate the salt!)
                .append(base64Encoder.encodeToString(saltedHash)); // base64-encode the salted hash and append it.

        // return a salt and salted hash combo.
        return result.toString();
    }

    private static byte[] concatArrays(byte[]... arrays) {
        int concatLength = 0;
        // get the actual length of all arrays and add it so we know how long our concatenated array has to be.
        for (int i = 0; i < arrays.length; i++) {
            concatLength = concatLength + arrays[i].length;
        }
        // prepare our concatenated array which we're going to return later.
        byte[] concatArray = new byte[concatLength];
        // this index tells us where we write into our array.
        int index = 0;
        // concatenate the arrays.
        for (int i = 0; i < arrays.length; i++) {
            for (int j = 0; j < arrays[i].length; j++) {
                concatArray[index] = arrays[i][j];
                index++;
            }
        }
        // return the concatenated arrays.
        return concatArray;
    }


}