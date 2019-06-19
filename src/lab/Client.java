package lab;

import java.io.*;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.channels.UnresolvedAddressException;
import java.nio.file.AccessDeniedException;
import java.util.LinkedList;
import java.util.List;

public class Client {
    private static boolean multiline = false;

    private static String serverAddress = "localhost";
    private static int serverPort = 8080;
    private static String username;

    private static Command previousCommand = null;

    public static void main(String[] args) {

        if (args.length > 0) {
            serverAddress = args[0];
            if (args.length > 1) {
                try {
                    serverPort = Integer.parseInt(args[1]);
                } catch (NumberFormatException e) {
                    e.getLocalizedMessage();
                }
            }
        }


        try {
            System.setOut(new PrintStream(System.out, true, "UTF-8"));
        } catch (UnsupportedEncodingException ignored) {
        }

        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

        System.out.println("Введите команду:\n");

        while (true) {
            try {
                System.out.print("> ");
                String query = multiline ? getMultilineCommand(reader) : reader.readLine();
                if (query == null) return;
                String response = processCommand(query);
                System.out.println(response);
            } catch (IOException e) {
                System.out.println("Не удалось прочитать стандартный поток вввода: " + e.getMessage());
            }
        }

    }

    /**
     * Выполняет первичную обработку команды. Может быть использован для команд,
     * выполняемых на клиенте без участия сервера
     *
     * @param query команда пользователя
     * @return результат операции для вывода на экран
     */
    private static String processCommand(String query) {
        query = query.trim().replaceAll("\\s{2,}", " ");
        Command command = new Command(query);
        if (((command.name).equals("login")) || ((command.name).equals("register")) || ((command.name).equals("repeat")) || (username != null)) {
            if (command.name.isEmpty())
                return "Введите команду";

            if (previousCommand == null)
                if (command.name.equals("repeat"))
                    return "repeat не должен быть первой командой. Введите help repeat, чтобы узнать больше";
                else
                    previousCommand = command;
            else if (command.name.equals("repeat"))
                command = previousCommand;
            else
                previousCommand = command;

            switch (command.name) {
                case "exit":
                    System.exit(0);

                case "show":
                    return doShow();

                case "address":
                    if (command.argument == null)
                        return "Адрес сервера: " + serverAddress +
                                "\nЧтобы изменить адрес, введите его после команды";
                    serverAddress = command.argument;
                    return "Установлен адрес " + serverAddress;
                case "port":
                    if (command.argument == null)
                        return "Порт: " + serverPort +
                                "\nЧтобы изменить порт, введите его после команды";
                    int newPort;
                    try {
                        newPort = Integer.parseInt(command.argument);
                    } catch (NumberFormatException e) {
                        newPort = -1;
                    }
                    if (newPort < 1 || newPort > 65535)
                        return "Порт должен быть числом от 1 до 65535";
                    serverPort = newPort;
                    return "Установлен порт " + serverPort;

                case "add":
                case "remove":
                case "add_min":
                    return doWithHatArgument(command.name, command.argument);

                case "multiline":
                    multiline = !multiline;
                    return "Многострочные команды " + (multiline ? "включены. Используйте ';' для завешения команды." : "выключены");

                case "import":
                    if (command.argument != null)
                        return doImport(command.argument);

                case "login":
                    return doLogin(command.name, command.argument);
                default:
                    return sendCommand(command.name, command.argument);
            }
        } else {
            return "вы не авторизированны";
        }
    }

    /**
     * Выполняет команду, аргумент которой является json-представлением экземпляра класса Hat
     *
     * @param command      имя команжы
     * @param jsonArgument аргумент команды
     * @return результат выполнения
     */
    private static String doWithHatArgument(String command, String jsonArgument) {
        try {
            if (jsonArgument == null)
                return sendCommand(command, null);
            else {
                Hat[] hats;
                try {
                    hats = HatMaker.generate(jsonArgument);
                    for (int i = 0; i < hats.length; i++) {
                        hats[i].setUser(Client.username);
                    }
                } catch (Exception e) {
                    return e.getMessage();
                }
                try (SocketChannel channel = SocketChannel.open()) {
                    channel.connect(new InetSocketAddress(serverAddress, serverPort));
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    ObjectOutputStream oos = new ObjectOutputStream(baos);

                    for (int i = 0; i < hats.length; i++) {
                        // Making a Message instance and writing it to ByteArrayOutputStream
                        Message message = new Message<>(command, hats[i], i + 1 == hats.length);
                        message.setUserName(Client.username);
                        oos.writeObject(message);
                    }

                    // Sending message using channel
                    ByteBuffer sendingBuffer = ByteBuffer.allocate(baos.size());
                    sendingBuffer.put(baos.toByteArray());
                    sendingBuffer.flip();
                    channel.write(sendingBuffer);

                    // Getting response
                    ObjectInputStream ois = new ObjectInputStream(channel.socket().getInputStream());
                    while (true) {
                        Message incoming = (Message) ois.readObject();
                        System.out.println(incoming.getMessage());
                        if (incoming.hasEndFlag())
                            break;
                    }
                } catch (UnresolvedAddressException e) {
                    return "Не удалось определить адрес сервера. Воспользуйтесь командой address, чтобы изменить адрес.";
                } catch (UnknownHostException e) {
                    return "Ошибка подключения к серверу: неизвестный хост. Воспользуйтесь командой address, чтобы изменить адрес";
                } catch (SecurityException e) {
                    return "Нет разрешения на подключение, проверьте свои настройки безопасности";
                } catch (ConnectException e) {
                    return "Нет соединения с сервером. Введите repeat, чтобы попытаться ещё раз, или измените адрес (команда address)";
                } catch (IOException e) {
                    return "Ошибка ввода-вывода: " + e;
                } catch (ClassNotFoundException e) {
                    return "Ошибка: клиент отправил данные в недоступном для клиента формате (" + e.getLocalizedMessage() + ")";
                }
            }
        } catch (Exception e) {
            return e.getMessage();
        }
        return "";
    }

    /**
     * Выполняет команлу show, вывод отправляет в System.out
     *
     * @return пустую строку или сообщение (возможно, об ошибке)
     */
    private static String doShow() {
        try (SocketChannel channel = SocketChannel.open()) {
            channel.connect(new InetSocketAddress(serverAddress, serverPort));

            // Creating a Message instance and writing it to ByteArrayOutputStream
            Message message = new Message("show", true);
            message.setUserName(username);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            new ObjectOutputStream(baos).writeObject(message);

            // Sending the message to server using channel
            ByteBuffer byteBuffer = ByteBuffer.allocate(baos.size());
            byteBuffer.put(baos.toByteArray()).flip();
            channel.write(byteBuffer);

            ObjectInputStream ois = new ObjectInputStream(channel.socket().getInputStream());
            List<Hat> result = new LinkedList<>();

            // Reading the response
            while (!channel.socket().isClosed()) {
                Message incoming = (Message) ois.readObject();
                if (!incoming.hasArgument())
                    break;
                if (incoming.getArgument() instanceof Hat)
                    result.add((Hat) incoming.getArgument());
                else
                    return "Сервер вернул данные в неверном формате";
                if (incoming.hasEndFlag())
                    break;
            }

            // Writing the response to System.out
            if (result.size() > 0) {
                System.out.println("Шляпы в гардеробе");
                for (Hat hat : result) {
                    System.out.println(hat.showHat());
                }
            } else
                return "Гардероб пуст";
            return "";
        } catch (UnresolvedAddressException e) {
            return "Не удалось определить адрес сервера. Воспользуйтесь командой address, чтобы изменить адрес.";
        } catch (UnknownHostException e) {
            return "Ошибка подключения к серверу: неизвестный хост. Воспользуйтесь командой address, чтобы изменить адрес";
        } catch (SecurityException e) {
            return "Нет разрешения на подключение, проверьте свои настройки безопасности";
        } catch (ConnectException e) {
            return "Нет соединения с сервером. Введите repeat, чтобы попытаться ещё раз, или измените адрес (команда address)";
        } catch (EOFException e) {
            return "";
        } catch (IOException e) {
            return "Ошибка ввода-вывода: " + e;
        } catch (ClassNotFoundException e) {
            return "Сервер отпавил класс, который не может прочитать клиент";
        }
    }

    /**
     * Формирует команду import и отправляет на сервер
     *
     * @param filename имя файла, содержимое которого будет отправлено
     * @return пустую строку или сообщение об ошибке, если есть
     */
    private static String doImport(String filename) {
        try {
            String content = FileLoader.getFileContent(filename);
            return sendCommand("import", content);
        } catch (FileNotFoundException e) {
            return "Нет такого файла";
        } catch (AccessDeniedException e) {
            return "Нет доступа к файлу";
        } catch (IOException e) {
            return "Ошибка ввода-вывода: " + e.getMessage();
        } catch (Exception e) {
            return "Неизвестная ошибка: " + e.toString();
        }
    }

    private static String doLogin(String name, String argument) {
        try (SocketChannel channel = SocketChannel.open()) {
            channel.connect(new InetSocketAddress(serverAddress, serverPort));

            // Making a Message instance and writing it to ByteArrayOutputStream
            Message message = new Message<>(name, argument, true);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(baos);
            oos.writeObject(message);

            // Sending message using channel
            ByteBuffer sendingBuffer = ByteBuffer.allocate(baos.size());
            sendingBuffer.put(baos.toByteArray());
            sendingBuffer.flip();
            new Thread(() -> {
                try {
                    channel.write(sendingBuffer);
                } catch (IOException ignored) {
                }
            }).start();

            // Getting Message instance from response
            ObjectInputStream ois = new ObjectInputStream(channel.socket().getInputStream());
            while (true) {
                Message incoming = (Message) ois.readObject();
                System.out.println(incoming.getMessage());
                String mess[] = incoming.getMessage().split(" ");
                try {
                    Client.username = mess[3];
                } catch (ArrayIndexOutOfBoundsException e) {
                }
                if (incoming.hasEndFlag())
                    break;
            }
            return "";
        } catch (UnresolvedAddressException e) {
            return "Не удалось определить адрес сервера. Воспользуйтесь командой address, чтобы изменить адрес.";
        } catch (UnknownHostException e) {
            return "Ошибка подключения к серверу: неизвестный хост. Воспользуйтесь командой address, чтобы изменить адрес";
        } catch (SecurityException e) {
            return "Нет разрешения на подключение, проверьте свои настройки безопасности";
        } catch (ConnectException e) {
            return "Нет соединения с сервером. Введите repeat, чтобы попытаться ещё раз, или измените адрес (команда address)";
        } catch (IOException e) {
            return "Ошибка ввода-вывода, обработка запроса прервана";
        } catch (ClassNotFoundException e) {
            return "Ошибка: клиент отправил данные в недоступном для клиента формате (" + e.getLocalizedMessage() + ")";
        }
    }

    /**
     * Отправляет команду на сервер, результат отправляет в System.out,
     * использует каналы согласно условию задания
     *
     * @param name     команда, которую нужно отправить
     * @param argument аргумент команды
     * @return пустую строку или сообщение об ошибке, если есть
     */
    private static String sendCommand(String name, Serializable argument) {
        try (SocketChannel channel = SocketChannel.open()) {
            channel.connect(new InetSocketAddress(serverAddress, serverPort));

            // Making a Message instance and writing it to ByteArrayOutputStream
            Message message = new Message<>(name, argument, true);
            message.setUserName(username);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(baos);
            oos.writeObject(message);

            // Sending message using channel
            ByteBuffer sendingBuffer = ByteBuffer.allocate(baos.size());
            sendingBuffer.put(baos.toByteArray());
            sendingBuffer.flip();
            new Thread(() -> {
                try {
                    channel.write(sendingBuffer);
                } catch (IOException ignored) {
                }
            }).start();

            // Getting Message instance from response
            ObjectInputStream ois = new ObjectInputStream(channel.socket().getInputStream());
            while (true) {
                Message incoming = (Message) ois.readObject();
                System.out.println(incoming.getMessage());
                if (incoming.hasEndFlag())
                    break;
            }
            return "";
        } catch (UnresolvedAddressException e) {
            return "Не удалось определить адрес сервера. Воспользуйтесь командой address, чтобы изменить адрес.";
        } catch (UnknownHostException e) {
            return "Ошибка подключения к серверу: неизвестный хост. Воспользуйтесь командой address, чтобы изменить адрес";
        } catch (SecurityException e) {
            return "Нет разрешения на подключение, проверьте свои настройки безопасности";
        } catch (ConnectException e) {
            return "Нет соединения с сервером. Введите repeat, чтобы попытаться ещё раз, или измените адрес (команда address)";
        } catch (IOException e) {
            return "Ошибка ввода-вывода, обработка запроса прервана";
        } catch (ClassNotFoundException e) {
            return "Ошибка: клиент отправил данные в недоступном для клиента формате (" + e.getLocalizedMessage() + ")";
        }
    }

    /**
     * Вощвращает слелующую команду пользователя. Предназначен для многострочного ввода.
     *
     * @param reader поток, из которого будет читаться команда
     * @return введённая пользователем команда
     * @throws IOException если что-то пойдёт не так
     */
    private static String getMultilineCommand(BufferedReader reader) throws IOException {
        StringBuilder builder = new StringBuilder();
        char current;
        boolean inString = false;
        do {
            current = (char) reader.read();
            if (current != ';' || inString)
                builder.append(current);
            if (current == '"')
                inString = !inString;
        } while (current != ';' || inString);
        return builder.toString();
    }

}