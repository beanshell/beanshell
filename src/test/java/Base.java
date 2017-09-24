/**
 * The Class Base.
 */
public class Base {

    /** The s. */
    public String s = null;
    /** The i. */
    public int i;

    /**
     * Instantiates a new base.
     */
    public Base() {}

    /**
     * Instantiates a new base.
     *
     * @param s
     *            the s
     */
    public Base(final String s) {
        this.s = s;
    }

    /**
     * Instantiates a new base.
     *
     * @param i
     *            the i
     */
    public Base(final int i) {
        this.i = i;
    }

    /**
     * Instantiates a new base.
     *
     * @param s
     *            the s
     * @param i
     *            the i
     */
    public Base(final String s, final int i) {
        this.s = s;
        this.i = i;
    }

    /**
     * Base method.
     *
     * @return the string
     */
    public String baseMethod() {
        return "baseMethod";
    }

    /**
     * Base method 2.
     *
     * @return the string
     */
    public String baseMethod2() {
        return "baseMethod2";
    }

    /**
     * Base method 3.
     *
     * @return the string
     */
    public String baseMethod3() {
        return "baseMethod3";
    }
}
