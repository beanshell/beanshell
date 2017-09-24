package mypackage;

/**
 * The Class Accessibility2.
 */
public class Accessibility2 extends Accessibility1 {

    /**
     * Instantiates a new accessibility 2.
     *
     * @param a
     *            the a
     * @param b
     *            the b
     */
    Accessibility2(final int a, final int b) {}

    /**
     * Instantiates a new accessibility 2.
     *
     * @param a
     *            the a
     */
    protected Accessibility2(final int a) {}

    /**
     * Instantiates a new accessibility 2.
     */
    public Accessibility2() {}

    /** The field 1. */
    private int field1 = 1;
    /** The field 2. */
    int field2 = 2;
    /** The field 3. */
    protected int field3 = 3;
    /** The field 4. */
    public int field4 = 4;

    /**
     * Gets the b1.
     *
     * @return the b1
     */
    private int getB1() {
        return 1;
    }

    /**
     * Gets the b2.
     *
     * @return the b2
     */
    int getB2() {
        return 2;
    }

    /**
     * Gets the b3.
     *
     * @return the b3
     */
    protected int getB3() {
        return 3;
    }

    /**
     * Gets the b4.
     *
     * @return the b4
     */
    public int getB4() {
        return 4;
    }

    /**
     * Sget 1.
     *
     * @return the int
     */
    private static int sget1() {
        return 1;
    }

    /**
     * Sget 1.
     *
     * @param a
     *            the a
     * @return the int
     */
    private static int sget1(int a) {
        return 1;
    }

    /**
     * Sget 2.
     *
     * @return the int
     */
    static int sget2() {
        return 2;
    }

    /**
     * Sget 3.
     *
     * @return the int
     */
    protected static int sget3() {
        return 3;
    }

    /**
     * Sget 4.
     *
     * @return the int
     */
    public static int sget4() {
        return 4;
    }

    /** The sfield 1. */
    private static int sfield1 = 1;
    /** The sfield 2. */
    static int sfield2 = 2;
    /** The sfield 3. */
    protected static int sfield3 = 3;
    /** The sfield 4. */
    public static int sfield4 = 4;
}
