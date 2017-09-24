/**
 * The Class MethodSelection.
 */
public class MethodSelection {

    /** The constructed with. */
    public Class constructedWith;

    /**
     * Instantiates a new method selection.
     *
     * @param o
     *            the o
     */
    public MethodSelection(final Object o) {
        this.constructedWith = o.getClass();
        System.out.println("selected object constr");
    }

    /**
     * Instantiates a new method selection.
     *
     * @param o
     *            the o
     */
    public MethodSelection(final String o) {
        this.constructedWith = o.getClass();
        System.out.println("selected string constr");
    }

    /**
     * Instantiates a new method selection.
     *
     * @param o
     *            the o
     */
    public MethodSelection(final long o) {
        this.constructedWith = Long.TYPE;
        System.out.println("selected long constr");
    }

    /**
     * Instantiates a new method selection.
     *
     * @param o
     *            the o
     */
    public MethodSelection(final int o) {
        this.constructedWith = Integer.TYPE;
        System.out.println("selected int constr");
    }

    /**
     * Instantiates a new method selection.
     *
     * @param o
     *            the o
     */
    public MethodSelection(final byte o) {
        this.constructedWith = Byte.TYPE;
        System.out.println("selected byte constr");
    }

    /**
     * Instantiates a new method selection.
     *
     * @param o
     *            the o
     */
    public MethodSelection(final short o) {
        this.constructedWith = Short.TYPE;
        System.out.println("selected short constr");
    }

    /**
     * Instantiates a new method selection.
     */
    public MethodSelection() {
        this.constructedWith = Void.TYPE;
        System.out.println("no args constr");
    }

    /**
     * Gets the static method for Object parameter.
     *
     * @param o
     *            the o
     * @return the static
     */
    public static Class get_static(final Object o) {
        System.out.println("selected object method");
        return o.getClass();
    }

    /**
     * Gets the static method for String parameter.
     *
     * @param o
     *            the o
     * @return the static
     */
    public static Class get_static(final String o) {
        System.out.println("selected string method");
        return o.getClass();
    }

    /**
     * Gets the static method for primitive int parameter.
     *
     * @param o
     *            the o
     * @return the static
     */
    public static Class get_static(final int o) {
        System.out.println("selected int method");
        return Integer.TYPE;
    }

    /**
     * Gets the static method for primitive long parameter.
     *
     * @param o
     *            the o
     * @return the static
     */
    public static Class get_static(final long o) {
        System.out.println("selected long method");
        return Long.TYPE;
    }

    /**
     * Gets the static method for primitive byte parameter.
     *
     * @param o
     *            the o
     * @return the static
     */
    public static Class get_static(final byte o) {
        System.out.println("selected byte method");
        return Byte.TYPE;
    }

    /**
     * Gets the static method for primitive short parameter.
     *
     * @param o
     *            the o
     * @return the static
     */
    public static Class get_static(final short o) {
        System.out.println("selected short method");
        return Short.TYPE;
    }

    /**
     * Gets the static.
     *
     * @return the static
     */
    public static Class get_static() {
        System.out.println("selected no args method");
        return Void.TYPE;
    }

    /**
     * Gets the dynamic method selection for Object.
     *
     * @param o
     *            the o
     * @return the dynamic
     */
    public Class get_dynamic(final Object o) {
        System.out.println("selected object method");
        return o.getClass();
    }

    /**
     * Gets the dynamic method selection for String.
     *
     * @param o
     *            the o
     * @return the dynamic
     */
    public Class get_dynamic(final String o) {
        System.out.println("selected string method");
        return o.getClass();
    }

    /**
     * Gets the dynamic method selection for primitive int.
     *
     * @param o
     *            the o
     * @return the dynamic
     */
    public Class get_dynamic(final int o) {
        System.out.println("selected int method");
        return Integer.TYPE;
    }

    /**
     * Gets the dynamic method selection for primitive long.
     *
     * @param o
     *            the o
     * @return the dynamic
     */
    public Class get_dynamic(final long o) {
        System.out.println("selected long method");
        return Long.TYPE;
    }

    /**
     * Gets the dynamic method selection for primitive byte.
     *
     * @param o
     *            the o
     * @return the dynamic
     */
    public Class get_dynamic(final byte o) {
        System.out.println("selected byte method");
        return Byte.TYPE;
    }

    /**
     * Gets the dynamic method selection for primitive short.
     *
     * @param o
     *            the o
     * @return the dynamic
     */
    public Class get_dynamic(final short o) {
        System.out.println("selected short method");
        return Short.TYPE;
    }

    /**
     * Gets the dynamic method selection for no args.
     *
     * @return the dynamic
     */
    public Class get_dynamic() {
        System.out.println("selected no args method");
        return Void.TYPE;
    }

    /**
     * Static vs dynamic 1.
     *
     * @param obj
     *            the obj
     * @return the class
     *
     *         If we try to invoke an instance method through a static context
     *         javac will error... rather than take the widening match.
     *         See methodselection2.bsh
     */
    public static Class staticVsDynamic1(final Object obj) {
        System.out.println("Object");
        return Object.class;
    }

    /**
     * Static vs dynamic 1.
     *
     * @param obj
     *            the obj
     * @return the class
     */
    public Class staticVsDynamic1(final String obj) {
        System.out.println("String");
        return String.class;
    }

    /**
     * The main method.
     *
     * @param args
     *            the arguments
     */
    public static void main(final String[] args) {
        System.out.println("should be string");
        new MethodSelection().staticVsDynamic1("foo");
        System.out.println("should be object");
        new MethodSelection();
        MethodSelection.staticVsDynamic1(new Object());
    }

    /**
     * Foo.
     *
     * @param x
     *            the x
     * @return the string
     */
    private String foo(Integer x) {
        return "private";
    }

    /**
     * Foo.
     *
     * @param x
     *            the x
     * @return the string
     */
    public String foo(final String x) {
        return "public";
    }
}
