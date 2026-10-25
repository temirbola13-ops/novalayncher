package app.nova.core;
import java.io.IOException;
public final class HashMismatchException extends IOException {
    public HashMismatchException(String expected, String actual) {
        super("SHA-1 mismatch: expected " + expected + ", got " + actual);
    }
}
