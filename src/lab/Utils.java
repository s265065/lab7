package lab;

public class Utils {


    /**
     * Возвращает количество информации в читабельном виде.
     * Например, при входных данных 134217728, возвращает "128 МиБ"
     * @param bytes Количество информации в байтах
     * @return Читабельное представление информации
     */
    public static String optimalInfoUnit(long bytes) {
        String[] units = {"байт", "КиБ", "МиБ", "ГиБ", "ТиБ"};
        long result = bytes;
        int divided = 0;
        while (result > 1024) {
            result /= 1024;
            divided++;
        }
        if (divided >= units.length)
            divided = units.length-1;
        return result + " " + units[divided];
    }
}
