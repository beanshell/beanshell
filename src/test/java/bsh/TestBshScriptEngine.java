package bsh;


import static javax.script.ScriptContext.ENGINE_SCOPE;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.contains;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.Writer;

import javax.script.Bindings;
import javax.script.Compilable;
import javax.script.CompiledScript;
import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import bsh.engine.BshScriptEngine;
import bsh.engine.BshScriptEngineFactory;

public class TestBshScriptEngine {
    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void test_bsh_script_engine( ) throws Throwable {
        ScriptEngineManager manager =
            new ScriptEngineManager( bsh.Interpreter.class.getClassLoader() );

        ScriptEngine engine = manager.getEngineByName( "beanshell" );
        assertNotNull( engine );

        // basic eval
        int i = (Integer)engine.eval("2*2");
        assertEquals( 4, i );

        // set a variable
        engine.put( "foo", 42 );
        assertEquals( 42, engine.get("foo") );

        // bsh primitives stay primitive internally
        engine.eval( "int fooInt=42" );
        assertEquals( 42, engine.get("foo") );
        assertSame( engine.eval("fooInt.getClass();"), Primitive.class );
        assertThat( engine.getContext().getAttribute( "fooInt", ENGINE_SCOPE ),
            instanceOf( Integer.class ) );

        // Variables visible through bindings in both directions?
        Bindings engineScope = engine.getBindings( ENGINE_SCOPE );
        Bindings engineScope2 = engine.getContext().getBindings( ENGINE_SCOPE );
        assertSame( engineScope, engineScope2 );
        assertThat( engineScope.get("foo"), instanceOf( Integer.class ) );
        engineScope.put("bar", "gee");
        // get() and eval() for us should be equivalent in this case
        assertEquals( "gee", engine.get("bar") );
        assertEquals( "gee", engine.eval("bar") );

        // install and invoke a method
        engine.eval("foo() { return foo+1; }");
        // invoke a method
        Invocable invocable = (Invocable) engine;
        assertEquals( 43, invocable.invokeFunction( "foo" ) );

        // get interface
        engine.eval("flag=false; run() { flag=true; }");
        assertFalse( (Boolean) engine.get("flag") );
        assertNull( engine.get("flag_nonexistent") );
        Runnable runnable = (Runnable) invocable.getInterface( Runnable.class );
        runnable.run();
        assertTrue( (Boolean) engine.get("flag") );

        // get interface from scripted object
        engine.eval(
            "flag2=false; myObj() { run() { flag2=true; } return this; }");
        assertFalse( (Boolean) engine.get("flag2") );
        Object scriptedObject = invocable.invokeFunction("myObj");
        assertThat( scriptedObject, instanceOf( This.class ) );
        runnable =
            (Runnable) invocable.getInterface( scriptedObject, Runnable.class );
        runnable.run();
        assertTrue( (Boolean) engine.get("flag2") );
        // Run with alternate bindings
        assertTrue( (Boolean) engine.get("flag") );
        assertEquals( 42, engine.get("foo") );
        Bindings newEngineScope = engine.createBindings();
        engine.eval( "flag=false; foo=33;", newEngineScope );
        assertFalse( (Boolean) newEngineScope.get("flag") );
        assertEquals(33, newEngineScope.get("foo") );
        // These are unchanged in default context
        assertTrue( (Boolean )engine.get("flag") );
        assertEquals( 42, engine.get("foo") );

        // Try redirecting output
        String fname = "testBshScriptEngine.out";
        String outString = "Data 1 2 3.";
        Writer fout = new FileWriter( fname );
        engine.getContext().setWriter( fout );
        engine.put( "outString", outString );
        engine.eval(new StringReader("print(outString);"));
        BufferedReader bin = new BufferedReader( new FileReader( fname ) );
        String line = bin.readLine();
        assertEquals( outString, line );
        new File(fname).delete();
        bin.close();
        fout.close();

        // Add a new scope dynamically?

    }

    @Test
    public void test_bsh_script_engine_compile() throws Throwable {
        Compilable engine = (Compilable) new ScriptEngineManager().getEngineByName( "beanshell" );
        assertNotNull( engine );
        CompiledScript script = engine.compile(new StringReader("return 42;"));
        assertEquals(42, script.eval());
    }

    @Test
    public void check_BshScriptEngineFactory() throws Exception {
        final String script = "a = null; return \"a=\" + a;\n";
        final Interpreter bsh = new Interpreter();
        final Object interpreterResult = bsh.eval(script);
        bsh.close();
        final BshScriptEngine bse = new BshScriptEngine();
        final ScriptEngineFactory bsef = bse.getFactory();
        final Object scriptEngineResult = bsef.getScriptEngine().eval(script);
        assertEquals(interpreterResult, scriptEngineResult);
        assertSame(bsef, bse.getFactory());
    }

    @Test
    public void test_script_engine_factory() throws Exception {
        final ScriptEngineFactory bsef = new BshScriptEngine().getFactory();
        assertEquals("BeanShell Engine", bsef.getParameter(ScriptEngine.ENGINE));
        assertEquals("BeanShell Engine", bsef.getParameter(ScriptEngine.NAME));
        assertEquals(Interpreter.VERSION, bsef.getParameter(ScriptEngine.ENGINE_VERSION));
        assertEquals("BeanShell", bsef.getParameter(ScriptEngine.LANGUAGE));
        assertEquals(Interpreter.VERSION, bsef.getParameter(ScriptEngine.LANGUAGE_VERSION));
        assertEquals("MULTITHREADED", bsef.getParameter("THREADING"));
        assertNull(bsef.getParameter("UNKNOWN"));
        assertEquals("foo();", bsef.getMethodCallSyntax(null, "foo", new String[0]));
        assertEquals("obj.toString();", bsef.getMethodCallSyntax("obj", "toString", new String[0]));
        assertEquals("obj.someMethod(42, \"text\", false);",
            bsef.getMethodCallSyntax("obj", "someMethod", new String[] {"42", "\"text\"", "false"} ));
        assertEquals("print(\"out\");", bsef.getOutputStatement("out"));
        assertEquals("a=0;\nb=a+1;\nreturn b;\n", bsef.getProgram("a=0","b=a+1","return b;"));
        assertThat(bsef.getMimeTypes(), contains("application/x-beanshell", "application/x-bsh"));
        assertThat(bsef.getExtensions(), contains("bsh"));
        assertThat(bsef.getNames(), contains("beanshell", "bsh"));
    }

    @Test
    public void check_parse_exception_line_number() throws Exception {
        thrown.expect(ScriptException.class);
        thrown.expectMessage(containsString("Encountered  \"(\" (  at line 1, column 6"));

        final String script = "print(\"test\";";
        new BshScriptEngineFactory().getScriptEngine().eval(script);
    }

    @Test
    public void check_script_exception_eval_error() throws Exception {
        thrown.expect(ScriptException.class);
        thrown.expectMessage(containsString("Unknown class: Unknown"));

        final String script = "new Unknown();";
        new BshScriptEngineFactory().getScriptEngine().eval(script);
    }

    @Test
    public void check_exception_thrown_in_script() throws Exception {
        thrown.expect(ScriptException.class);
        thrown.expectMessage(containsString("Target exception: java.lang.Exception: test exception"));

        final String script = "throw new Exception('test exception');";
        new BshScriptEngineFactory().getScriptEngine().eval(script);
    }

    @Test
    public void check_script_exception_get_interface_null_this() throws Exception {
        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage(containsString("Illegal object type: null"));

        ((Invocable) new BshScriptEngine()).getInterface(null, null);
    }

    @Test
    public void check_script_exception_get_interface_illegal_this() throws Exception {
        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage(containsString("Illegal object type: class java.lang.Object"));

        ((Invocable) new BshScriptEngine()).getInterface(new Object(), null);
    }

    @Test
    public void check_script_exception_invoke_method_null_this() throws Exception {
        thrown.expect(ScriptException.class);
        thrown.expectMessage(containsString("Illegal object type: null"));

        ((Invocable) new BshScriptEngine()).invokeMethod(null, null);
    }

    @Test
    public void check_script_exception_invoke_method_illegal_this() throws Exception {
        thrown.expect(ScriptException.class);
        thrown.expectMessage(containsString("Illegal object type: class java.lang.Object"));

        ((Invocable) new BshScriptEngine()).invokeMethod(new Object(), null);
    }

    @Test
    public void check_script_exception_invoke_function_unknown() throws Exception {
        thrown.expect(ScriptException.class);
        thrown.expectMessage(containsString("Method unknown() not found in bsh scripted object"));

        ((Invocable) new BshScriptEngine()).invokeFunction("unknown");
    }

    @Test
    public void check_script_exception_invoke_function_throws_exception() throws Exception {
        thrown.expect(ScriptException.class);
        thrown.expectMessage(containsString("Target exception: java.lang.Exception: test exception"));

        ScriptEngine engine =  new ScriptEngineManager().getEngineByName("beanshell");
        engine.eval("foo() { throw new Exception('test exception'); }");
        ((Invocable) engine).invokeFunction("foo");
    }

    @Test
    public void check_script_exception_compile_close_ioe() throws Exception {
        thrown.expect(ScriptException.class);
        thrown.expectMessage(containsString("Test Close IOE"));

        Reader read = new BufferedReader( new StringReader("return 42;") ) {
            public void close() throws IOException {
                throw new IOException("Test Close IOE");
            }
        };
        ((Compilable) new ScriptEngineManager().getEngineByName("beanshell")).compile(read);
    }

    @Test
    public void check_script_exception_eval_close_ioe() throws Exception {
        thrown.expect(ScriptException.class);
        thrown.expectMessage(containsString("Test Close IOE"));

        @SuppressWarnings("resource")
        Reader read = new BufferedReader( new StringReader("return 42;") ) {
            public void close() throws IOException {
                throw new IOException("Test Close IOE");
            }
        };
        new ScriptEngineManager().getEngineByName("beanshell").eval(read);
    }

    @Test
    public void check_script_exception_compile_parse_exception() throws Exception {
        thrown.expect(ScriptException.class);
        thrown.expectMessage(containsString("Encountered  \"(\" (  at line 1, column 20"));

        final String script = "print(\"test\";";
        ((Compilable) new BshScriptEngineFactory().getScriptEngine()).compile(script);
    }

    @Test
    public void check_script_exception_compile_eval_error() throws Exception {
        thrown.expect(ScriptException.class);
        thrown.expectMessage(containsString("Unknown class: Unknown"));

        final String script = "new Unknown();";
        ((Compilable) new ScriptEngineManager().getEngineByName("beanshell")).compile(script).eval();
    }

    @Test
    public void check_script_exception_compile_reader_ioe() throws Exception {
        thrown.expect(ScriptException.class);
        thrown.expectMessage(containsString("Test IOE"));

        Reader read = new StringReader("") {
            public int read(char cbuf[]) throws IOException {
                throw new IOException("Test IOE");
            }
        };
        ((Compilable) new BshScriptEngineFactory().getScriptEngine()).compile(read);
    }
}
