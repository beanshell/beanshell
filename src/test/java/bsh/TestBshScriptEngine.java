package bsh;


import static javax.script.ScriptContext.ENGINE_SCOPE;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.Writer;

import javax.script.Bindings;
import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import javax.script.SimpleBindings;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import bsh.engine.BshScriptEngineFactory;

public class TestBshScriptEngine {
    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void test_bsh_script_engine( ) throws Throwable {
        ScriptEngineManager manager =
            new ScriptEngineManager( bsh.Interpreter.class.getClassLoader() );

        ScriptEngine engine = manager.getEngineByName( "beanshell" );
        assertTrue( engine != null );

        // basic eval
        int i = (Integer)engine.eval("2*2");
        assert( i == 4 );

        // set a variable
        engine.put( "foo", 42 );
        assertTrue( (Integer)engine.get("foo") == 42 );

        // bsh primitives stay primitive internally
        engine.eval( "int fooInt=42" );
        assertTrue( (Integer)engine.get("foo") == 42 );
        assertTrue( engine.eval("fooInt.getClass()") == bsh.Primitive.class );
        assertTrue( engine.getContext().getAttribute( "fooInt", ENGINE_SCOPE )
            instanceof Integer );

        // Variables visible through bindings in both directions?
        Bindings engineScope = engine.getBindings( ENGINE_SCOPE );
        Bindings engineScope2 = engine.getContext().getBindings( ENGINE_SCOPE );
        assertTrue( engineScope == engineScope2 );
        assertTrue( engineScope.get("foo") instanceof Integer );
        engineScope.put("bar", "gee");
        // get() and eval() for us should be equivalent in this case
        assertTrue( engine.get("bar").equals("gee") );
        assertTrue( engine.eval("bar").equals("gee") );

        // install and invoke a method
        engine.eval("foo() { return foo+1; }");
        // invoke a method
        Invocable invocable = (Invocable) engine;
        int foo = (Integer)invocable.invokeFunction( "foo" );
        assertTrue( foo == 43 );

        // get interface
        engine.eval("flag=false; run() { flag=true; }");
        assertTrue( (Boolean)engine.get("flag") == false );
        assertTrue( (Boolean)engine.get("flag_nonexistent") == null );
        Runnable runnable = (Runnable)invocable.getInterface( Runnable.class );
        runnable.run();
        assertTrue( (Boolean)engine.get("flag") == true );

        // get interface from scripted object
        engine.eval(
            "flag2=false; myObj() { run() { flag2=true; } return this; }");
        assertTrue( (Boolean)engine.get("flag2") == false );
        Object scriptedObject = invocable.invokeFunction("myObj");
        assertTrue( scriptedObject instanceof bsh.This );
        runnable =
            (Runnable)invocable.getInterface( scriptedObject, Runnable.class );
        runnable.run();
        assertTrue( (Boolean)engine.get("flag2") == true );

        // Run with alternate bindings
        assertTrue( (Boolean)engine.get("flag") == true );
        assertTrue( (Integer)engine.get("foo") ==42 );
        Bindings newEngineScope = new SimpleBindings();
        engine.eval( "flag=false; foo=33;", newEngineScope );
        assertTrue( (Boolean)newEngineScope.get("flag") == false );
        assertTrue( (Integer)newEngineScope.get("foo") == 33 );
        // These are unchanged in default context
        assertTrue( (Boolean)engine.get("flag") == true );
        assertTrue( (Integer)engine.get("foo") ==42 );

        // Try redirecting output
        String fname = "testBshScriptEngine.out";
        String outString = "Data 1 2 3.";
        Writer fout = new FileWriter( fname );
        engine.getContext().setWriter( fout );
        engine.put( "outString", outString );
        engine.eval("print(outString)");
        BufferedReader bin = new BufferedReader( new FileReader( fname ) );
        String line = bin.readLine();
        assertTrue( line.equals( outString ));
        new File(fname).delete();

        // compile
        // ...

        // Add a new scope dynamically?

    }

    @Test
    public void check_BshScriptEngineFactory() throws Exception {
        final String script = "a = null; return \"a=\" + a;\n";
        final Object interpreterResult = new Interpreter().eval(script);
        final Object scriptEngineResult = new BshScriptEngineFactory().getScriptEngine().eval(script);
        assertEquals(interpreterResult, scriptEngineResult);
    }

    @Test
    public void check_ParseExceptionLineNumber() throws Exception {
        thrown.expect(ScriptException.class);
        thrown.expectMessage(containsString("Encountered  \"(\" (  at line 1, column 6"));

        final String script = "print(\"test\";";
        new BshScriptEngineFactory().getScriptEngine().eval(script);
    }
}
