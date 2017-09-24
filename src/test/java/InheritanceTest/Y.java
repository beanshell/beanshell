package InheritanceTest;

/**
 * The Class Y.
 */
public class Y extends X implements C {

    /** {@inheritDoc} */
    public void c() {
        System.out.println("Y.c()");
    }

    /**
     * Y.
     */
    public void y() {
        System.out.println("Y.y()");
    }

    /**
     * Y.
     */
    void y_() {
        System.out.println("Y.y_()");
    }
}
