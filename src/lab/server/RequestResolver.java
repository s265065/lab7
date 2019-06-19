package lab.server;

import lab.Hat;
import lab.Message;
import lab.Utils;

import java.io.*;
import java.net.Socket;
import java.nio.file.AccessDeniedException;
import java.sql.SQLException;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

class RequestResolver implements Runnable {

    private static int maxRequestSize = 268435456;
    private static long maxLoggableRequestSize = 128;
    private static DataBaseConnection db;
    private ObjectOutputStream out;
    private ObjectInputStream ois;
    private Wardrobe wardrobe;
    private Socket socket;
    private Logger logger;
    private String autosave = "autosave.csv";

    RequestResolver(Socket socket, Wardrobe wardrobe, Logger logger, DataBaseConnection db) {
        try {
            out = new ObjectOutputStream(socket.getOutputStream());
            ois = new ObjectInputStream(
                    new LimitedInputStream(socket.getInputStream(), maxRequestSize)
            );
            this.socket = socket;
            this.wardrobe = wardrobe;
            this.logger = logger;
            this.db = new DataBaseConnection();



        } catch (IOException e) {
            logger.err("Ошибка создания решателя запроса: " + e.toString());
        }
    }

    @Override
    public void run() {
        try {
            List<Message> messages = new LinkedList<>();

            while (true) {
                Object incoming = ois.readObject();

                if (incoming instanceof Message) {
                    messages.add((Message) incoming);
                    if (((Message) incoming).hasEndFlag())
                        break;
                } else {
                    sendEndMessage("Клиент отправил данные в неверном формате");
                    return;
                }
            }

            if (messages.size() == 1) {
                Message message = messages.get(0);
                if (message.getMessage().length() <= maxLoggableRequestSize)
                    logger.log("Запрос от " + socket.getInetAddress() + ": " + message.getMessage());
                else
                    logger.log("Запрос от " + socket.getInetAddress() + ", размер запроса: " + Utils.optimalInfoUnit(message.getMessage().length()));

                processMessage(message);
            } else {
                logger.log("Запрос из " + messages.size() + " сообщений от " + socket.getInetAddress());
                for (int i = 0; i < messages.size(); i++)
                    processMessage(messages.get(i), i + 1 == messages.size());
            }

        } catch (LimitAchievedException e) {
            logger.err("Клиент (" + socket.getInetAddress() + ") отправил слишком много данных, в запросе отказано");
            sendEndMessage("Ваш запрос слишком большой, он должен быть не больше " + Utils.optimalInfoUnit(maxRequestSize));
        } catch (EOFException e) {
            logger.err("Сервер наткнулся на неожиданный конец");
            sendEndMessage("Не удалось обработать ваш запрос: в ходе чтения запроса сервер наткнулся на неожиданный конец данных");
        } catch (IOException e) {
            logger.err("Ошибка исполнения запроса: " + e.toString());
            sendEndMessage("На сервере произошла ошибка: " + e.toString());
        } catch (ClassNotFoundException e) {
            sendEndMessage("Клиент отправил данные в неверном формате");
        }
    }

    /**
     * Отправляет сообщение, отмеченное как последнее
     *
     * @param message текст сообщения
     */
    private void sendEndMessage(String message) {
        sendMessage(message, true);
    }

    /**
     * Отправляет сообщение с указанным флагом окончания
     *
     * @param message текст сообщения
     * @param endFlag флаг окончания
     */
    private void sendMessage(String message, boolean endFlag) {
        try {
            out.writeObject(new Message(message, endFlag));
        } catch (IOException e) {
            logger.log("Ошибка отправки данных клиенту: " + e.getLocalizedMessage());
        }
    }

    /**
     * Обрабатывает сообщение, отправляемый клиенту результат будет отмечен как последний
     *
     * @param message сообщение
     */
    private void processMessage(Message message) {
        processMessage(message, true);
    }

    /**
     * Обрабатывает сообщение
     *
     * @param message сообщение
     * @param endFlag если он true, результат обработки отправится клиенту как последний
     */
    private <T extends Serializable> void processMessage(Message message, boolean endFlag) {
        if (message == null) {
            sendMessage("Задан пустой запрос", endFlag);
            return;
        }

        switch (message.getMessage()) {
            case "info":
                sendMessage(wardrobe.info(), endFlag);
                return;

            case "show":
                try {
                    Hat[] hats = new Hat[0];
                    hats = wardrobe.toArray(hats);
                    Arrays.sort(hats);
                    for (int i = 0, hatsLength = hats.length; i < hatsLength; i++)
                        out.writeObject(new Message<>("", hats[i], i + 1 == hatsLength));
                    if (wardrobe.size() == 0)
                        out.writeObject(new Message<>("", null));
                } catch (IOException e) {
                    logger.log("Ошибка исполнения запроса show: " + e.getLocalizedMessage());
                }
                return;

            case "save":
                if (wardrobe != null) {
                    db.saveHats(wardrobe, (String) message.getArgument());
                    sendEndMessage("Гардероб сохранён в файл");
                }
                sendEndMessage("Гардероб пуст, сохранять нечего");
                return;

            case "load":
                if (!message.hasArgument()) {
                    sendMessage("Имя не указано.\n" +
                            "Введите \"help load\", чтобы узнать, как пользоваться командой", endFlag);
                    return;
                }
                try {
                    if (!(message.getArgument() instanceof String)) {
                        sendMessage("Клиент отправил запрос в неверном формате (аргумент сообщения должен быть строкой)", endFlag);
                        return;
                    }
                    wardrobe.clear();
                    db.clear();
                    WardrobeLoaderSaver.load(wardrobe, (String) message.getArgument(), this.db, message.getUserName());
                    sendMessage("Загрузка успешна", endFlag);
                } catch (AccessDeniedException e) {
                    sendMessage("Нет доступа для чтения", endFlag);
                } catch (FileNotFoundException e) {
                    sendMessage("Файл не найден", endFlag);
                } catch (IOException e) {
                    sendEndMessage("На сервере произошла ошибка чтения/записи");
                } catch (WardrobeOverflowException e) {
                    sendMessage("В шляпе не осталось места, некоторые существа загрузились", endFlag);
                } catch (SQLException e) {
                    sendEndMessage("Ошибка при очистке базы данных");
                }
                return;

            case "import":
                if (!message.hasArgument()) {
                    sendMessage("Имя не указано.\n" +
                            "Введите \"help import\", чтобы узнать, как пользоваться командой", endFlag);
                    return;
                }
                try {
                    if (!(message.getArgument() instanceof String)) {
                        sendMessage("Клиент отправил запрос в неверном формате (аргумент сообщения должен быть строкой)", endFlag);
                        return;
                    }
                    WardrobeLoaderSaver.imload(wardrobe, (String) message.getArgument(), this.db, message.getUserName());
                    sendMessage("Загрузка успешна! В гардеробе " + wardrobe.size() + " шляп", endFlag);
                    db.saveHats(wardrobe, autosave);
                } catch (WardrobeOverflowException e) {
                    sendMessage("В гардеробе не остмалось места, некоторые шляпы не загрузились", endFlag);
                }
                return;

            case "add":
                try {
                    if (!message.hasArgument()) {
                        sendMessage(helpFor(message.getMessage()), endFlag);
                        return;
                    }
                    if (!(message.getArgument() instanceof Hat)) {
                        sendMessage("Клиент отправил данные в неверном формате (аргумент должен быть сериализованным объектом)", endFlag);
                        return;
                    }
                    Hat hat = (Hat) message.getArgument();
                    if (wardrobe.addH(hat, message.getUserName())) {
                        sendMessage(hat.getHatColor() + " добавлена в гардероб", endFlag);
                        db.addToDB(hat, message.getUserName());
                    }
                    db.saveHats(wardrobe, autosave);
                    return;
                } catch (WardrobeOverflowException e) {
                    sendMessage("Недостаточно места в гардеробе. " +
                            "В гардероб может поместиться не больше " + Wardrobe.getMaxCollectionElements() + " шляп.\n" +
                            "Попробуйте удалить кого-то, чтобы освободить место.", endFlag);
                } catch (Exception e) {
                    sendMessage("Не получилось создать существо: " + e.getMessage(), endFlag);
                }
                return;

            case "add_min":
                try {
                    if (!message.hasArgument()) {
                        sendMessage(helpFor(message.getMessage()), endFlag);
                        return;
                    }
                    if (!(message.getArgument() instanceof Hat)) {
                        sendMessage("Клиент отправил данные в неверном формате (аргумент должен быть сериализованным объектом)", endFlag);
                        return;
                    }
                    if (wardrobe.addIfMin((Hat) message.getArgument(), message.getUserName())) {
                        sendMessage("Шляпа добавлена", endFlag);
                        db.addToDB((Hat) message.getArgument(), message.getUserName());
                        db.saveHats(wardrobe, autosave);
                    } else sendMessage("Шляпа не добавлена", endFlag);
                    return;
                } catch (Exception e) {
                    sendMessage(e.getMessage(), endFlag);
                }

            case "remove":
                try {
                    if (!message.hasArgument()) {
                        sendMessage(helpFor(message.getMessage()), endFlag);
                        return;
                    }
                    if (!(message.getArgument() instanceof Hat)) {
                        sendMessage("Клиент отправил данные в неверном формате (аргумент должен быть сериализованным объектом)", endFlag);
                        return;
                    }
                    boolean removed = wardrobe.remove((Hat) message.getArgument(), message.getUserName());
                    if (removed) {
                        sendMessage("Шляпа удалена", endFlag);
                        db.removeHat((Hat) message.getArgument(), message.getUserName());
                        db.saveHats(wardrobe, autosave);
                    } else
                        sendMessage("У Вас доступе не нашлось такой шляпы. Удалять можно только свои шляпы.", endFlag);
                } catch (Exception e) {
                    sendMessage(e.getMessage(), endFlag);
                }
                return;

            case "help":
                if (!message.hasArgument())
                    sendMessage(helpFor("help"), endFlag);
                else {
                    if (message.getArgument() instanceof String)
                        sendMessage(helpFor((String) message.getArgument()), endFlag);
                    else
                        sendMessage("Клент отправил данные в неверном формате (аргумент должен быть строкой)", endFlag);
                }
                return;

            case "register":
                if (message.hasArgument()) {
                    try {
                        String args[] = message.getArgument().toString().split(" ");
                        String usernameS = args[0];
                        String mailS = args[1];
                        if (args.length > 2) {
                            String passwordS = args[2];
                            int resultR = db.executeRegister(usernameS, mailS, passwordS);
                            if (resultR == 1) {
                                sendEndMessage("Регистрация успешна");
                            } else if (resultR == 0) {
                                sendEndMessage("Вы уже зарегестрированны");
                            } else {
                                sendEndMessage("Ошибка при регистрации");
                            }
                        } else {
                            int resultR = db.executeRegister(usernameS, mailS, (new Integer(Math.round((ZonedDateTime.now()).getNano()))).toString());
                            if (resultR == 1) {
                                sendEndMessage("Регистрация успешна");
                            } else if (resultR == 0) {
                                sendEndMessage("Вы уже зарегестрированны");
                            } else {
                                sendEndMessage("Ошибка при регистрации");
                            }
                        }
                    } catch (NullPointerException e) {
                        sendEndMessage("Невернный ввод. Вы должны ввести имя пользователя почту и (при желании) пароль разделенный одним пробелом");
                    } catch (ArrayIndexOutOfBoundsException e) {
                        sendEndMessage("Невернный ввод. Вы должны ввести имя пользователя почту и (при желании) пароль разделенный одним пробелом");
                    }
                } else {
                    sendEndMessage("Укажите имя пользователя и почту при желании пароль");
                }
                break;
            case "login":
                try {
                    String args[] = message.getArgument().toString().split(" ");
                    String usernameS = args[0];
                    String passwordS = args[1];
                    int result = db.executeLogin(usernameS, passwordS);
                    if (result == 0) {
                        sendEndMessage("Вы вошли как " + usernameS);
                    } else if (result == 1) {
                        sendEndMessage("Сначала нужно зарегестрироваться!");
                    } else if (result == 2) {
                        sendEndMessage("Неверный пароль!");
                    } else {
                        sendEndMessage("Не могу войти");
                    }
                } catch (NullPointerException e) {
                    sendEndMessage("Невернный ввод. Вы должны ввести имя пользователя и пароль");
                } catch (ArrayIndexOutOfBoundsException e) {
                    sendEndMessage("Невернный ввод. Вы должны ввести имя пользователя и пароль ");
                }
                break;

            default:
                if (message.getMessage().length() < 64)
                    sendMessage("Неизвестная команда " + message.getMessage() + ", введите help, чтобы получить помощь", endFlag);
                else
                    sendMessage("Неизвестная большая команда, введите help, чтобы получить помощь", endFlag);
        }
    }

    /**
     * Возвращает инструкции к команде
     *
     * @param command команда, для которой нужна инструкция
     * @return инструкция к указанной команде
     */
    private static String helpFor(String command) {
        switch (command) {
            case "help":
                return "Вот команды, которыми можно пользоваться:\n\n" +
                        "exit - выход\n" +
                        "address [newAddress] - информация об адресе сервера. Если указан адрес, он будет заменён\n" +
                        "port [newPort] - информация о порте сервера. Если указан порт, он будет заменён\n" +
                        "return - выполнить предыдущую введённую команду\n" +
                        "info - информация о гардеробе\n" +
                        "show - показать гардероб\n" +
                        "save {файл} - сохранить в файл\n" +
                        "add_min - добавить шляпу, если она расположена ниже всех имеющихся\n" +
                        "add {elem} - добавить шляпу, обязательные поля: size, num, color; дополнительно contents\n" +
                        "multiline - включить/выключить ввод в несколько строк\n" +
                        "import {file} - добавить данные из файла клиента в коллекцию\n" +
                        "load {file} - загрузить состояние коллекции из файла сервера\n" +
                        "save {file} - сохранить состояние коллекции в файл сервера\n" +
                        "remove {elem} - удалить шляпу\n" +
                        "help {command} - инструкция к команде\n" +
                        "help - показать этот текст";

            case "exit":
                return "Введите \"exit\", чтобы выйти";

            case "address":
                return "Если вызвать эту команду, то можно узнать адрес, к которому клиент будет\n" +
                        "подключаться и отправлять команды. Если после команды указать новый адрес,\n" +
                        "то текущий адрес заменится им.\n\n" +
                        "Например:\n" +
                        "> address 192.168.1.100\n" +
                        "> address 127.0.0.1\n" +
                        "> address localhost";

            case "port":
                return "Если вызвать эту команду, то можно узнать порт, по которому клиент будет\n" +
                        "подключаться и отправлять команды. Если после команды указать новый порт,\n" +
                        "то текущий порт заменится им. Порт должен быть в пределах от 1 до 65535.\n\n" +
                        "Например:\n" +
                        "> port 8080\n" +
                        "> port 80\n" +
                        "> port 21";

            case "repeat":
                return "Эта команда выполняет предыдущую введённую команду.\n" +
                        "Команда again никогда не бывает первой введённой командой и\n" +
                        "никогда не бывает \"предыдущей\" введённой командой.";

            case "info":
                return "Введите \"info\", чтобы узнать о количестве шляп в гардеробе, времени создания и типе используемой коллекции";

            case "show":
                return "Выводит список шляп в гардеробе";

            case "save {файл}":
                return "Введите \"save\", а затем имя файла, чтобы сохранить в него гардероб.\n" +
                        "Файл будет содержать список шляп в формате csv\n\n" +
                        "Например:\n" +
                        "> save saved_state.csv";


            case "add":
                return "Здесь вы можете добавить новую шляпу в гардероб.  \n" +
                        "Чтобы сделать это пожалуйста введите текст в формате json так, как представленно на примере \n " +
                        "\"{\"size\": <положительное целое число>, \"color\": <строка>}\" \n " +
                        "Если вы хотите создать шляпу, в которой сразу будут лежать каки-либо предметы, вам следует набрать следующий текст:  \n " +
                        " \"{\"size\": <целое положительное число>, \"color\": <строка>, \"contents\": [{\"Itemname\": <строка>}, ... {\"Itemname\": <строка>}]}\" \n " +
                        "Будьте внимательны! Размер шляпы(size) показывает какое количество предметов может в неё поместиться. \n " +
                        "Так, если вы попытаетесь при создании шляпы положить в неё больше предметов, чем позволяет размер,  \n" +
                        "будет создана шляпа в которой будут лежать первые несколько предметов по возможному количеству. \n " +
                        "Оставшиеся будут проигнорированны. \n " +
                        "Кроме того вы можете положить только предметы из этого списка: \n " +
                        "зубная щётка(TOOTHBRUSH), зубной порошок (DENTIFRIECE), мыло (SOAP), полотенце (TOWEL), носовой платок (CHIEF),\n " +
                        " носки (SOCKS), гвоздь (NAIL), проволока (COPPERWIRE).\n  " +
                        "Обязательно используйте английский язык и заглавные буквы при вводе названий предметов как указанно в скобках.\n " +
                        "Каждый из предметов может лежать в шляпе только в одном экземпляре. \n " +
                        "Если какой-то предмет будет введен несколько раз он будет добавлен только один раз.\n ";

            case "add_min":
                return "Эта команда идентична команде add (введите \"help add\", чтобы узнать о ней), но  \n" +
                        "в данном случае шляпа будет добавлена только в том случае, если она ниже всех уже имеющихся";

            case "multiline":
                return "Переключает режим многострочного ввода. Если многострочный ввод выключен, введите \"multiline\",\n" +
                        "чтобы включить его. После того, как вы включили многострочный режим, ваши команды будут\n" +
                        "отделяться друг от друга знаком ';'.\n" +
                        "Чтобы выключить многострочный ввод, введите \"multiline;\". Обратите внимание, что в режиме\n" +
                        "многострочного ввода также нужен знак ';' после команды отключения многострочного ввода.";

            case "import":
                return "Иногда бывает так, что нужно передать содержимое всего файла на сервер, где этого файла нет.\n" +
                        "Используйте команду \"import\", чтобы сделать это. После имени команды укажите файл,\n" +
                        "содержимое которого передастся на сервер.  Файл должен хранить данные в формате csv\n\n" +
                        "Например:\n" +
                        "> import client_file.csv";

            case "load":
                return "Эта команда идентична команде import (введите \"help import\", чтобы узнать о ней), но  \n" +
                        "load используется для загрузки файла сервера\n";

            case "save":
                return "Эта команда сохраняет состояние коллекции в файл сервера в формате csv.\n\n" +
                        "Например:\n" +
                        "> save server_file.csv";

            case "login":
                return "Эта команда испоользуется для входа в аккаунт." +
                        "Вместе с командой нужно ввести логи и пароль через один пробел";

            case "register":
                return "Команда чтобы зарегистрироваться. Необходимо ввести логин и почту. При желании можно ввести пароль. \n" +
                        "Если пароль не будет введён, то он сгенерируется автоматически";

            case "remove":
                return "Здесь вы можете удалить шляпу из гардероба. \n " +
                        "Это можно сделать по номеру шляпы или по ее цвету и размеру.\n " +
                        "Чтобы сделать это пожалуйста введите номер шляпы или задайте шляпу в формате json так, как представленно на примере  \n " +
                        "\"{\"size\": <положительное целое число>, \"color\": <строка>}\" \n " +
                        "Если в коллекции несколько шляп с такими характеристиками, будет удалена только первая найденная.\n ";

            default:
                return "Неизвестная команда " + command + "\n" +
                        "Введите \"help\", чтобы узнать, какие есть команды";
        }
    }

    /**
     * @return Максимальный размер запроса в байтах
     */
    static int getMaxRequestSize() {
        return maxRequestSize;
    }

    /**
     * Задаёт максимальный размер запроса
     *
     * @param maxRequestSize максимальный размер запроса в байтах
     */
    static void setMaxRequestSize(int maxRequestSize) {
        RequestResolver.maxRequestSize = maxRequestSize;
    }

    /**
     * Получает максимальный размер запроса, содержимое которого отобразится в локах.
     * Если размер запроса превысит данное значение, вместо сожержимого в локах будет указан размер запроса.
     *
     * @return Максимальный размер логгируемого содержимого запроса
     */
    static long getMaxLoggableRequestSize() {
        return maxLoggableRequestSize;
    }

    /**
     * Устанавливает максимальный размер запроса, содержимое которого отобразится в локах.
     * Если размер запроса превысит данное значение, вместо сожержимого в локах будет указан размер запроса.
     *
     * @param maxLoggableRequestSize Максимальный размер логгируемого содержимого запроса
     */
    static void setMaxLoggableRequestSize(long maxLoggableRequestSize) {
        RequestResolver.maxLoggableRequestSize = maxLoggableRequestSize;
    }
}