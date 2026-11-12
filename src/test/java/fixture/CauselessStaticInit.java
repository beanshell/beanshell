package fixture;

/** A class whose static initializer throws an ExceptionInInitializerError
 * that has no cause. */
public class CauselessStaticInit {
    public CauselessStaticInit() {
    }

    static {
        if (true)
            throw new ExceptionInInitializerError("no cause");
    }
}
