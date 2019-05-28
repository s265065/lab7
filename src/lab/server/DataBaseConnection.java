package lab.server;

import lab.Hat;


import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.*;
import java.time.ZonedDateTime;
import java.util.Iterator;
import java.util.Random;


public class DataBaseConnection {
    private String url = "jdbc:postgresql://localhost:5432/studs";
    private String name = "lab";
    private String pass = "lab1234";
    private Connection connection = null;

    {
        try {
            Class.forName("org.postgresql.Driver");
            System.out.println("Installed Driver");
            connection = DriverManager.getConnection(url, name, pass);
            System.out.println("The Connection is successfully established\n");
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Can't connect to the database");
        }
    }

    public Connection getConnection(){return this.connection;}

    public int loadHats(Wardrobe ward) {
        try {
            int i = 0;
            ZonedDateTime time = ZonedDateTime.now();
            PreparedStatement preStatement = connection.prepareStatement("SELECT * FROM \"hats\";");
            ResultSet result = preStatement.executeQuery();
            while (result.next()) {
                String username = result.getString("username");
                int size = result.getInt("size");
                String color = result.getString("color");
                int num = result.getInt("num");
                String date = result.getString("creation_date");
                if (date != null) {
                    time = ZonedDateTime.parse(result.getString("creation_date").replace("T", "Z"));
                }
                Hat h = new Hat(size,color,num);
                h.setCreatedDate(time);
                h.setUser(username);
                ward.add(h);
                i++;

            }
            return i;
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Error whilst adding Hats");
            return -1;
        }
    }

    public void addToDB(Hat h,String username) {
        try {
            addHat(h, username);
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Error while adding a hat to a DataBase");
        }
    }

    private void addHat(Hat h, String username) throws SQLException {
        PreparedStatement preStatement = connection.prepareStatement("INSERT INTO hats VALUES (?, ?, ?, ?, ?, ?);");
        preStatement.setInt(1,h.getSize());
        preStatement.setString(2,h.getColor());
        preStatement.setInt(3,h.getNum());
        preStatement.setString(4,h.contentlist());
        preStatement.setString(5,h.getCreatedDate().toString());
        preStatement.setString(6,username);
        preStatement.executeUpdate();
    }

    public void saveHats(Wardrobe hats) {
        try {
            if (hats != null) {

                Iterator<Hat> iterator = hats.iterator();

                while (iterator.hasNext()) {
                    Hat h = iterator.next();
                    addHat(h, h.getUser());
                }
                System.out.println("The DataBase has been updated.");
            } else {
                System.out.println("Collection is empty; nothing to save!");
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Error whilst saving Hats to the DataBase");
        }
    }

    public boolean removeHat(Hat hat, String username) {
        try {
        PreparedStatement preStatement = connection.prepareStatement("DELETE FROM hats WHERE size=? AND color=? AND num=? AND username=?;");
        preStatement.setInt(1, hat.getSize());
        preStatement.setString(2,hat.getColor());
        preStatement.setInt(3,hat.getNum());
        preStatement.setString(4,username);
        preStatement.executeUpdate();
        return true;
    } catch (Exception e) {
        System.out.println("Error while removing a hat from a DataBase");
        return false;
    }
         }

    public int executeLogin(String login, String hash) {
        try {
            PreparedStatement preStatement = connection.prepareStatement("SELECT * FROM users WHERE username=? and hash=?;");
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
            System.out.println("Login error");
            return -1;
        }
    }

    public int executeRegister(String login, String mail, String pass) {
        try {

            PreparedStatement ifLog = connection.prepareStatement("SELECT * FROM users WHERE username=?;");
            ifLog.setString(1,login);
            ResultSet result = ifLog.executeQuery();
            if (result.next()){return 0;}
            String hash = DataBaseConnection.encryptString(pass);
            PreparedStatement statement = connection.prepareStatement("INSERT INTO users VALUES (?, ?, ?);");
            statement.setString(1, login);
            statement.setString(2, mail);
            statement.setString(3, hash);
            statement.executeUpdate();
            new Thread(() -> JavaMail.registration(mail, pass)).start();
            return 1;
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Error whilst registration");
            return -1;
        }
    }

    public static String getToken() {
        try {
            int leftLimit = 97; // letter 'a'
            int rightLimit = 122; // letter 'z'
            int targetStringLength = 8;
            Random random = new Random(); // get random string
            StringBuilder buffer = new StringBuilder(targetStringLength);
            for (int i = 0; i < targetStringLength; i++) {
                int randomLimitedInt = leftLimit + (int)
                        (random.nextFloat() * (rightLimit - leftLimit + 1));
                buffer.append((char) randomLimitedInt);
            }

            //encrypt random string with SHA-512
            return buffer.toString();

        } catch (Exception e) {
            return null;
        }
    }

    public static String encryptString(String input)
    {
        try {
            // getInstance() method is called with algorithm SHA-512
            MessageDigest md = MessageDigest.getInstance("SHA-512");

            // digest() method is called
            // to calculate message digest of the input string
            // returned as array of byte
            byte[] messageDigest = md.digest(input.getBytes());

            // Convert byte array into signum representation
            BigInteger no = new BigInteger(1, messageDigest);

            // Convert message digest into hex value
            String hashtext = no.toString(16);

            // Add preceding 0s to make it 32 bit
            while (hashtext.length() < 32) {
                hashtext = "0" + hashtext;
            }

            // return the HashText
            return hashtext;
        }

        // For specifying wrong message digest algorithms
        catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}