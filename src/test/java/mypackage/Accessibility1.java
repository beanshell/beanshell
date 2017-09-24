package mypackage;

/**
 * The Class Accessibility1.
 */
class Accessibility1 {

    /**
     * Instantiates a new accessibility 1.
     *
     * @param a
     *            the a
     * @param b
     *            the b
     * @param c
     *            the c
     */
    private Accessibility1(int a, int b, int c) {}

    /**
     * Instantiates a new accessibility 1.
     *
     * @param a
     *            the a
     * @param b
     *            the b
     */
    Accessibility1(final int a, final int b) {}

    /**
     * Instantiates a new accessibility 1.
     *
     * @param a
     *            the a
     */
    protected Accessibility1(final int a) {}

    /**
     * Instantiates a new accessibility 1.
     */
    public Accessibility1() {}

    /** The field 1. */
    private int field1 = 1;
    /** The field 2. */
    int field2 = 2;
    /** The field 3. */
    protected int field3 = 3;
    /** The field 4. */
    public int field4 = 4;

    /**
     * Gets the 1.
     *
     * @return the 1
     */
    private int get1() {
        return 1;
    }

    /**
     * Gets the 1.
     *
     * @param a
     *            the a
     * @return the 1
     */
    private int get1(int a) {
        return 1;
    }

    /**
     * Gets the 2.
     *
     * @return the 2
     */
    int get2() {
        return 2;
    }

    /**
     * Gets the 3.
     *
     * @return the 3
     */
    protected int get3() {
        return 3;
    }

    /**
     * Gets the 4.
     *
     * @return the 4
     */
    public int get4() {
        return 4;
    }

    /**
     * Supersget 1.
     *
     * @return the int
     */
    private static int supersget1() {
        return 1;
    }

    /**
     * Supersget 1.
     *
     * @param a
     *            the a
     * @return the int
     */
    private static int supersget1(int a) {
        return 1;
    }

    /**
     * Supersget 2.
     *
     * @return the int
     */
    static int supersget2() {
        return 2;
    }

    /**
     * Supersget 3.
     *
     * @return the int
     */
    protected static int supersget3() {
        return 3;
    }

    /**
     * Supersget 4.
     *
     * @return the int
     */
    public static int supersget4() {
        return 4;
    }

    /** The supersfield 1. */
    private static int supersfield1 = 1;
    /** The supersfield 2. */
    static int supersfield2 = 2;
    /** The supersfield 3. */
    protected static int supersfield3 = 3;
    /** The supersfield 4. */
    public static int supersfield4 = 4;
}
