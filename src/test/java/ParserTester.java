import bsh.Interpreter;

/**
 * The Class ParserTester.
 */
public class ParserTester {

    /**
     * Eval.
     *
     * @param code
     *            the code
     * @return the object
     */
    public static Object eval(final String code) {
        final Interpreter interpreter = new Interpreter();
        try {
            interpreter.eval(code);
            return null;
        } catch (final Exception e) {
            return e;
        }
    }
}
