package lab.server;

class WardrobeOverflowException extends RuntimeException {
    WardrobeOverflowException() {
        super("Гардероб переполнен");
    }
}
