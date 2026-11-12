package mypackage;

import bsh.CallStack;
import bsh.Interpreter;

/** A compiled command that throws, found by importCommands("/mypackage"). */
public class failingCommand {
    public static Object invoke(Interpreter env, CallStack callstack,
            String message) {
        throw new IllegalStateException(message);
    }
}
