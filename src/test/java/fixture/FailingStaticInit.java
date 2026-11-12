package fixture;

/** A class whose static initializer throws. */
public class FailingStaticInit {
    public static int x;
    public static final int K = 7;

    public static int m() {
        return 1;
    }

    static {
        if (true)
            throw new IllegalStateException("init failed");
    }
}
