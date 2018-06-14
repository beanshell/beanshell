import bsh.Interpreter;

public class ParserTester {
    public static Object eval(String code) {
        try (Interpreter interpreter = new Interpreter()) {
            interpreter.eval(code);
            return null;
        } catch (Exception e) {
            return e;
        }
    }
}
