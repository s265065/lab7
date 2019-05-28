package lab;

public class NegativeShelfNumberException  extends RuntimeException {
        public NegativeShelfNumberException() {
            super("Номер полки не может быть отрицательнымм числом");
        }
    }
