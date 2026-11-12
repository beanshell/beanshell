package fixture;

/** A class whose static initializer records that it ran in a system
 * property named after the class. */
public class RecordingStaticInit {
    public static int x = 5;

    static {
        System.setProperty(RecordingStaticInit.class.getName(), "initialized");
    }
}
