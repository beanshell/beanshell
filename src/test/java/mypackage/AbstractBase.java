package mypackage;

/**
 * The Class AbstractBase.
 */
abstract class AbstractBase {

    // can we see a concrete public method in a package-private abstract base
    // class? The method will *not* be overridden in the public concrete class
    /**
     * Visible.
     *
     * @return true, if successful
     */
    public boolean visible() {
        return true;
    }
}
