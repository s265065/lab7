package lab.server;

import javax.activation.DataHandler;
import javax.activation.FileDataSource;
import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import java.util.Properties;


class JavaMail {
    static final String ENCODING = "UTF-8";

    public static void registration(String email, String reg_token){
        String subject = "Подтверждение регистрации";
        String content = "Ваш пароль: " + reg_token + ". Никому не показывайте его! Спасибо за регистрацию, вот вам случайный Майкл Шин как котик";
        String smtpHost = "smtp.gmail.com";
        String from = "lesti0505@gmail.com";
        String login = "lesti0505@gmail.com";
        String password = "yanerobot";
        String smtpPort="25";
        try {
            sendSimpleMessage(login, password, from, email, content, subject, smtpPort, smtpHost);
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Ошибка при отправке письма");
        }
    }

    public static void main(String args[]) {
        String subject = "Подтверждение регистрации";
        String content = "Ваш пароль: randompassword. Никому не показывайте его!  Спасибо за регистрацию, вот вам случайный Майкл Шин как котик";
        String smtpHost = "smtp.gmail.com";
        String from = "lesti0505@gmail.com";
        String login = "lesti0505@gmail.com";
        String password = "yanerobot";
        String smtpPort = "25";
        try {
            sendSimpleMessage(login, password, from, "lesti0505@gmail.com", content, subject, smtpPort, smtpHost);
        } catch (Exception e) {
            System.out.println(e.getMessage());
            System.out.println("Ошибка при отправке письма");
        }
    }

    private static MimeBodyPart createFileAttachment(String filepath)
            throws MessagingException {
        // Создание MimeBodyPart
        MimeBodyPart mbp = new MimeBodyPart();

        // Определение файла в качестве контента
        FileDataSource fds = new FileDataSource(filepath);
        mbp.setDataHandler(new DataHandler(fds));
        mbp.setFileName(fds.getName());
        return mbp;
    }

    private static void sendSimpleMessage(String login, String password, String from, String to, String content, String subject, String smtpPort, String smtpHost)
            throws MessagingException {
        Authenticator auth = new Auth(login, password);

        Properties props = System.getProperties();
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.port", smtpPort);
        props.put("mail.smtp.host", smtpHost);
        props.put("mail.smtp.auth", "true");
        props.put("mail.mime.charset", ENCODING);
        Session session = Session.getDefaultInstance(props, auth);

        Multipart mmp = new MimeMultipart();
        // Текст сообщения
        MimeBodyPart bodyPart = new MimeBodyPart();
        bodyPart.setContent(content, "text/plain; charset=utf-8");
        mmp.addBodyPart(bodyPart);
        // Вложение файла в сообщение
        Integer random_number1 = (int) (Math.random() * 18);

        MimeBodyPart mbr = createFileAttachment("src\\img\\" + random_number1.toString() + ".jpg");
        MimeBodyPart mbr2 = createFileAttachment("src\\img\\" + ((Integer) (random_number1 + 100)).toString() + ".jpg");
        mmp.addBodyPart(mbr);
        mmp.addBodyPart(mbr2);


        Message msg = new MimeMessage(session);
        msg.setFrom(new InternetAddress(from));
        msg.setRecipient(Message.RecipientType.TO, new InternetAddress(to));
        msg.setSubject(subject);
        msg.setContent(mmp);
        Transport.send(msg);
    }
}