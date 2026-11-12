package fixture;

/** A class whose static initializer throws an Error, which the JVM passes
 * on without an ExceptionInInitializerError around it. */
public class ErrorStaticInit {
    public static int x;

    public static int m() {
        return 1;
    }

    static {
        if (true)
            throw new AssertionError("init error");
    }
}
