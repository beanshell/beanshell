/**
 * The Class Fields.
 */
public class Fields {

    /** The static field. */
    public static boolean staticField;
    /** The static field 2. */
    public static int staticField2;
    /** The x. */
    public int x = 5;
    /** The short twenty two. */
    public static short shortTwentyTwo = 22;
    /** The prop test. */
    public static int propTest = 22;

    /**
     * Gets the fields.
     *
     * @return the fields
     */
    public static Fields getFields() {
        return new Fields();
    }

    /**
     * Gets the fields 2.
     *
     * @return the fields 2
     */
    public Fields getFields2() {
        return new Fields();
    }

    /** The ambig name. */
    // ambiguity in field vs method
    public String ambigName = "field";

    /**
     * Ambig name.
     *
     * @return the string
     */
    public String ambigName() {
        return "method";
    }
}
