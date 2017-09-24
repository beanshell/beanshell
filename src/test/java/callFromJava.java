import bsh.EvalError;
import bsh.Interpreter;

/**
 * The Class callFromJava.
 */
public class callFromJava {

    /**
     * The main method.
     *
     * @param argv
     *            the arguments
     * @throws EvalError
     *             the eval error
     */
    public static void main(final String argv[]) throws EvalError {
        final Interpreter interpreter = new Interpreter();
        interpreter.set("foo", 5);
        interpreter.eval("bar = foo*10");
        final Integer bar = (Integer) interpreter.get("bar");
        if (bar.intValue() != 50)
            System.out.println("FAILED...");
        else
            System.out.println("passed...");
    }
}
