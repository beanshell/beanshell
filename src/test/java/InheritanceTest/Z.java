package InheritanceTest;

/**
 * The Class Z.
 */
class Z extends X implements C {

    /** {@inheritDoc} */
    public void c() {
        System.out.println("Z.c()");
    }

    /**
     * Z.
     */
    public void z() {
        System.out.println("Z.z()");
    }

    /**
     * Z.
     */
    void z_() {
        System.out.println("Z.z_()");
    }
}
