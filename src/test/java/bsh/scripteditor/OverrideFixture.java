package bsh.scripteditor;

/**
 * A class with one method of each visibility, so tests can tell which new methods
 * in an anonymous subclass would override an inherited one.
 */
public abstract class OverrideFixture {

    public abstract String work();

    protected String hook() {
        return "hook";
    }

    @SuppressWarnings("unused")
    private String secret() {
        return "secret";
    }

    protected static String shared() {
        return "shared";
    }

    public String callHook() {
        return hook();
    }
}
