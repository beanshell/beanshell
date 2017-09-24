package bsh;

import static javax.script.ScriptContext.ENGINE_SCOPE;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;

import javax.script.Bindings;
import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import javax.script.SimpleBindings;

/**
 * The Class TestBshScriptEngine.
 */
public class TestBshScriptEngine {

    /**
     * The main method.
     *
     * @param args
     *            the arguments
     * @throws ScriptException
     *             the script exception
     * @throws NoSuchMethodException
     *             the no such method exception
     * @throws IOException
     *             Signals that an I/O exception has occurred.
     */
    public static void main(final String[] args)
            throws ScriptException, NoSuchMethodException, IOException {
        final ScriptEngineManager manager = new ScriptEngineManager(
                bsh.Interpreter.class.getClassLoader());
        final ScriptEngine engine = manager.getEngineByName("beanshell");
        assertTrue(engine != null);
        // basic eval
        final int i = (Integer) engine.eval("2*2");
        assert i == 4;
        // set a variable
        engine.put("foo", 42);
        assertTrue((Integer) engine.get("foo") == 42);
        // bsh primitives stay primitive internally
        engine.eval("int fooInt=42");
        assertTrue((Integer) engine.get("foo") == 42);
        assertTrue(engine.eval("fooInt.getClass()") == bsh.Primitive.class);
        assertTrue(engine.getContext().getAttribute("fooInt",
                ENGINE_SCOPE) instanceof Integer);
        // Variables visible through bindings in both directions?
        final Bindings engineScope = engine.getBindings(ENGINE_SCOPE);
        final Bindings engineScope2 = engine.getContext()
                .getBindings(ENGINE_SCOPE);
        assertTrue(engineScope == engineScope2);
        assertTrue(engineScope.get("foo") instanceof Integer);
        engineScope.put("bar", "gee");
        // get() and eval() for us should be equivalent in this case
        assertTrue(engine.get("bar").equals("gee"));
        assertTrue(engine.eval("bar").equals("gee"));
        // install and invoke a method
        engine.eval("foo() { return foo+1; }");
        // invoke a method
        final Invocable invocable = (Invocable) engine;
        final int foo = (Integer) invocable.invokeFunction("foo");
        assertTrue(foo == 43);
        // get interface
        engine.eval("flag=false; run() { flag=true; }");
        assertTrue((Boolean) engine.get("flag") == false);
        assertTrue((Boolean) engine.get("flag_nonexistent") == null);
        Runnable runnable = invocable.getInterface(Runnable.class);
        runnable.run();
        assertTrue((Boolean) engine.get("flag") == true);
        // get interface from scripted object
        engine.eval(
                "flag2=false; myObj() { run() { flag2=true; } return this; }");
        assertTrue((Boolean) engine.get("flag2") == false);
        final Object scriptedObject = invocable.invokeFunction("myObj");
        assertTrue(scriptedObject instanceof bsh.This);
        runnable = invocable.getInterface(scriptedObject, Runnable.class);
        runnable.run();
        assertTrue((Boolean) engine.get("flag2") == true);
        // Run with alternate bindings
        assertTrue((Boolean) engine.get("flag") == true);
        assertTrue((Integer) engine.get("foo") == 42);
        final Bindings newEngineScope = new SimpleBindings();
        engine.eval("flag=false; foo=33;", newEngineScope);
        assertTrue((Boolean) newEngineScope.get("flag") == false);
        assertTrue((Integer) newEngineScope.get("foo") == 33);
        // These are unchanged in default context
        assertTrue((Boolean) engine.get("flag") == true);
        assertTrue((Integer) engine.get("foo") == 42);
        // Try redirecting output
        System.out.println("Before redirect, stdout...");
        final String fname = "testBshScriptEngine.out";
        final String outString = "Data 1 2 3.";
        final Writer fout = new FileWriter(fname);
        engine.getContext().setWriter(fout);
        engine.put("outString", outString);
        engine.eval("print(outString)");
        final BufferedReader bin = new BufferedReader(new FileReader(fname));
        final String line = bin.readLine();
        assertTrue(line.equals(outString));
        new File(fname).delete();
        // compile
        // ...
        // Add a new scope dynamically?
    }

    /**
     * Assert true.
     *
     * @param cond
     *            the cond
     */
    static void assertTrue(final boolean cond) {
        if (cond)
            System.out.println("Passed...");
        else
            throw new Error("assert failed...");
    }
}
