/**
 * test misc. generation of exceptions
 */
public class Exceptions {

    /**
     * Throw runtime.
     */
    public static void throwRuntime() {
        throw new RuntimeException();
    }

    /**
     * Throw arithmetic.
     */
    public static void throwArithmetic() {
        throw new ArithmeticException();
    }

    /**
     * Throw exception.
     *
     * @throws Exception
     *             the exception
     */
    public static void throwException() throws Exception {
        throw new Exception();
    }
}
