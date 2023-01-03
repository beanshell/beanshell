package bsh;


import static javax.script.ScriptContext.ENGINE_SCOPE;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.startsWith;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertThrows;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.reflect.Method;

import javax.script.Bindings;
import javax.script.Compilable;
import javax.script.CompiledScript;
import javax.script.Invocable;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import javax.script.SimpleScriptContext;

import org.junit.Test;

import bsh.engine.BshScriptEngine;
import bsh.engine.BshScriptEngineFactory;

public class TestBshScriptEngine {

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
        assertEquals( 42, engine.get("fooInt") );
        engine.eval("fooInt++");
        assertEquals( 43, engine.get("fooInt"));
        assertTrue( (boolean)engine.eval("fooInt.getClass().isPrimitive();") );
        assertSame( Integer.TYPE, engine.eval("fooInt.getClass();") );
        assertSame( Integer.TYPE, engine.eval("fooInt.getType();") );
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

        // Test variables in global scope
        manager.put("e", 44);
        assertEquals(44, manager.get("e"));
        assertEquals(44, engine.eval("e"));
        engine.eval("e=45");
        assertEquals(45, manager.get("e"));
        assertEquals(45, engine.eval("e"));
        engine.eval("e=\"hello\"");
        assertEquals("hello", manager.get("e"));
        assertEquals("hello", engine.eval("e"));

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
        String outString = "checkstyle-supressions.xml";
        Writer fout = new FileWriter( fname );
        engine.getContext().setWriter( fout );
        engine.put( "outString", outString );
        engine.eval(new StringReader("dir('src/conf');"));
        BufferedReader bin = new BufferedReader( new FileReader( fname ) );
        String line = bin.readLine();
        assertNotNull(line);
        assertThat(line, endsWith(outString));
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
    public void test_bsh_script_engine_compile_no_line_terminator() throws Throwable {
        Compilable engine = (Compilable) new ScriptEngineManager().getEngineByName( "beanshell" );
        assertNotNull( engine );
        CompiledScript script = engine.compile("37+5");
        assertEquals(42, script.eval());
    }

    @Test
    public void test_bsh_script_engine_compile_args() throws Throwable {
        ScriptEngine engine = new ScriptEngineManager().getEngineByName( "beanshell" );
        assertNotNull( engine );
        CompiledScript square = ((Compilable) engine).compile("return x*x;");
        ScriptContext ctx = engine.getContext();
        ctx.setAttribute("x", 5, ENGINE_SCOPE);
        assertEquals(25, square.eval(ctx));
    }

    @Test
    public void test_bsh_script_engine_compile_return_this() throws Throwable {
        class CompiledMethod {
            Object bshThis;
            Method invokeMethod;
            CompiledMethod() throws Throwable {
                Compilable engine = (Compilable) new ScriptEngineManager().getEngineByName( "beanshell" );
                assertNotNull( engine );
                CompiledScript script = engine.compile("square(x) { return x*x; } return this;");
                bshThis = script.eval();
                invokeMethod = bshThis.getClass().getMethod("invokeMethod", new Class[] {String.class, Object[].class});
            }
            int square(int x) throws Throwable {
                return (int) invokeMethod.invoke(bshThis, new Object[] {"square", new Object[] {x}});
            }
        }
        CompiledMethod cm = new CompiledMethod();
        assertEquals(16, cm.square(4));
        assertEquals(25, cm.square(5));
    }

    @Test
    public void test_bsh_script_engine_this_invoke_method() throws Throwable {
        ScriptEngine engine = new ScriptEngineManager().getEngineByName( "beanshell" );
        ScriptContext ctx = new SimpleScriptContext();
        StringWriter sw = new StringWriter();
        ctx.setWriter(sw);
        engine.setContext(ctx);
        String outString = "checkstyle-supressions.xml";
        This bshThis = (This) engine.eval("square(x) { return x*x; } return this;");

        assertEquals(16, bshThis.invokeMethod("square", new Object[] {4}));
        assertEquals(25, bshThis.invokeMethod("square", new Object[] {5}));
        assertEquals(bshThis.hashCode(), bshThis.invokeMethod("hashCode", null));
        assertEquals(bshThis.toString(), bshThis.invokeMethod("toString", null));
        assertTrue((boolean)bshThis.invokeMethod("equals", new Object[] {bshThis}));
        assertFalse((boolean)bshThis.invokeMethod("equals", new Object[] {this}));
        assertThat(""+bshThis.invokeMethod("clone", null),
                startsWith("'this' reference to Bsh object: NameSpace: javax_script_context clone"));

        bshThis.invokeMethod("dir", new Object[] {"src/conf"});
        assertThat(sw.toString(), containsString(outString));
        bshThis.invokeMethod("print", new Object[] {"foo bar"});
        assertThat(sw.toString(), endsWith("foo bar"+System.lineSeparator()));

        EvalError e = assertThrows(EvalError.class, () -> bshThis.invokeMethod("unknown", null));
        assertThat(e.getMessage(), containsString("Method unknown() not found in bsh scripted object"));
        assertThat(e.getCause().getMessage(), containsString("Command not found: unknown()"));
        e = assertThrows(EvalError.class, () -> bshThis.invokeMethod("equals", null));
        assertThat(e.getMessage(), containsString("Method equals() not found in bsh scripted object"));
        assertThat(e.getCause().getMessage(), containsString("Command not found: equals()"));
        e = assertThrows(EvalError.class, () -> bshThis.invokeMethod("hashCode", new Object[] {2}));
        assertThat(e.getMessage(), containsString("Method hashCode(Integer) not found in bsh scripted object"));
        assertThat(e.getCause().getMessage(), containsString("Command not found: hashCode(Integer)"));
        e = assertThrows(EvalError.class, () -> bshThis.invokeMethod("toString", new Object[] {2}));
        assertThat(e.getMessage(), containsString("Method toString(Integer) not found in bsh scripted object"));
        assertThat(e.getCause().getMessage(), containsString("Command not found: toString(Integer)"));
        e = assertThrows(EvalError.class, () -> bshThis.invokeMethod("clone", new Object[] {2}));
        assertThat(e.getMessage(), containsString("Method clone(Integer) not found in bsh scripted object"));
        assertThat(e.getCause().getMessage(), containsString("Command not found: clone(Integer)"));
        e = assertThrows(EvalError.class, () -> bshThis.invokeMethod("dir", new Object[] {2}));
        assertThat(e.getMessage(), containsString("Method dir(Integer) not found in bsh scripted object"));
        assertThat(e.getCause().getMessage(), containsString("Error invoking compiled command"));
        e = assertThrows(EvalError.class, () -> bshThis.invokeMethod("cd", new Object[] {2}));
        assertThat(e.getMessage(), containsString("Method cd(Integer) not found in bsh scripted object"));
        assertThat(e.getCause().getMessage(), containsString("Command not found: cd(Integer)"));
        e = assertThrows(EvalError.class, () -> bshThis.invokeMethod("square", new Object[] {""}));
        assertThat(e.getMessage(), containsString("Use of non + operator with String"));
        assertThat(e.getCause().getMessage(), containsString("Use of non + operator with String"));
    }

    @Test
    public void test_bsh_script_engine_compile_set_return_this() throws Throwable {
        ScriptEngine engine = new ScriptEngineManager().getEngineByName( "beanshell" );
        assertNotNull( engine );
        CompiledScript script = ((Compilable) engine).compile("square(x) { return x*x; } return this;");
        engine.getContext().setAttribute("script", script.eval(), ENGINE_SCOPE);
        assertEquals(16, engine.eval("script.square(4);"));
        assertEquals(25, engine.eval("script.square(5);"));
        engine.eval("square(x) { return script.square(x); }");
        assertEquals(16, engine.eval("square(4);"));
        assertEquals(25, engine.eval("square(5);"));
    }

    @Test
    public void check_BshScriptEngineFactory() throws Exception {
        final String script = "a = null; return \"a=\" + a;\n";
        final Interpreter bsh = new Interpreter();
        final Object interpreterResult = bsh.eval(script);
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
    public void test_eval_writer_unicode() throws Exception {
        ScriptEngine engine = new BshScriptEngineFactory().getScriptEngine();
        ScriptContext ctx = new SimpleScriptContext();
        StringWriter sw = new StringWriter();
        ctx.setWriter(sw);
        engine.setContext(ctx);
        engine.eval("print('\\u3456');");
        assertEquals(new String("\u3456".getBytes(), "UTF-8").charAt(0),
                new String(sw.toString().getBytes(), "UTF-8").charAt(0));
    }

    @Test
    public void test_method_invocation() throws Exception {
        ScriptEngine engine = new BshScriptEngineFactory().getScriptEngine();
        ScriptContext ctx = new SimpleScriptContext();
        StringWriter sw = new StringWriter();
        ctx.setWriter(sw);
        engine.setContext(ctx);
        engine.eval(
                "this.interpreter.print(new Object() {"
                 + "public String toString() {"
                      +"return \"hello BeanShell\";"
                 + "}"
              + "});"
            );
        assertEquals("hello BeanShell", sw.toString());
        sw.close();
    }

    @Test
    public void check_parse_exception_line_number() throws Exception {
        final String script = "print(\"test\";";
        ScriptException e = assertThrows(ScriptException.class, () ->
            new BshScriptEngineFactory().getScriptEngine().eval(script));
        assertThat(e.getMessage(), containsString("Encountered: ;"));
    }

    @Test
    public void check_script_exception_eval_error() throws Exception {
        final String script = "new Unknown();";
        ScriptException e = assertThrows(ScriptException.class, () ->
            new BshScriptEngineFactory().getScriptEngine().eval(script));
        assertThat(e.getMessage(), containsString("Unknown class: Unknown"));
    }

    @Test
    public void check_exception_thrown_in_script() throws Exception {
        final String script = "throw new Exception('test exception');";
        ScriptException e = assertThrows(ScriptException.class, () ->
            new BshScriptEngineFactory().getScriptEngine().eval(script));
        assertThat(e.getMessage(), containsString("Caused by: java.lang.Exception: test exception"));
    }

    @Test
    public void check_script_exception_get_interface_null_this() throws Exception {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () ->
            ((Invocable) new BshScriptEngine()).getInterface(null, null));
        assertThat(e.getMessage(), containsString("Illegal object type: null"));
    }

    @Test
    public void check_script_exception_get_interface_illegal_this() throws Exception {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () ->
            ((Invocable) new BshScriptEngine()).getInterface(new Object(), null));
        assertThat(e.getMessage(), containsString("Illegal object type: class java.lang.Object"));
    }

    @Test
    public void check_script_exception_invoke_method_null_this() throws Exception {
        ScriptException e = assertThrows(ScriptException.class, () ->
            ((Invocable) new BshScriptEngine()).invokeMethod(null, null));
        assertThat(e.getMessage(), containsString("Illegal object type: null"));
    }

    @Test
    public void check_script_exception_invoke_method_illegal_this() throws Exception {
        ScriptException e = assertThrows(ScriptException.class, () ->
            ((Invocable) new BshScriptEngine()).invokeMethod(new Object(), null));
        assertThat(e.getMessage(), containsString("Illegal object type: class java.lang.Object"));
    }

    @Test
    public void check_script_exception_invoke_function_unknown() throws Exception {
        ScriptException e = assertThrows(ScriptException.class, () ->
            ((Invocable) new BshScriptEngine()).invokeFunction("unknown"));
        assertThat(e.getMessage(), containsString("Method unknown() not found in bsh scripted object"));
     }

    @Test
    public void check_script_exception_invoke_function_throws_exception() throws Exception {
        ScriptEngine engine =  new ScriptEngineManager().getEngineByName("beanshell");
        engine.eval("foo() { throw new Exception('test exception'); }");
         ScriptException e = assertThrows(ScriptException.class, () ->
            ((Invocable) engine).invokeFunction("foo"));
        assertThat(e.getMessage(), containsString("Caused by: java.lang.Exception: test exception"));
    }

    @Test
    public void check_script_exception_compile_close_ioe() throws Exception {
        Reader read = new BufferedReader( new StringReader("return 42;") ) {
            public void close() throws IOException {
                throw new IOException("Test Close IOE");
            }
        };
        ;
        ScriptException e = assertThrows(ScriptException.class, () ->
            ((Compilable) new ScriptEngineManager().getEngineByName("beanshell")).compile(read));
        assertThat(e.getMessage(), containsString("Test Close IOE"));
    }

    @Test
    public void check_script_exception_compile_parse_exception() throws Exception {
        final String script = "print(\"test\";";
        ScriptException e = assertThrows(ScriptException.class, () ->
            ((Compilable) new BshScriptEngineFactory().getScriptEngine()).compile(script));
        assertThat(e.getMessage(), containsString("Encountered: ;"));
    }

    @Test
    public void check_script_exception_compile_eval_error() throws Exception {
        final String script = "new Unknown();";
        ScriptException e = assertThrows(ScriptException.class, () ->
            ((Compilable) new ScriptEngineManager().getEngineByName("beanshell")).compile(script).eval());
        assertThat(e.getMessage(), containsString("Unknown class: Unknown"));
    }

    @Test
    public void check_script_exception_compile_reader_ioe() throws Exception {
        Reader read = new StringReader("") {
            public int read(char cbuf[]) throws IOException {
                throw new IOException("Test IOE");
            }
        };
        ScriptException e = assertThrows(ScriptException.class, () ->
            ((Compilable) new BshScriptEngineFactory().getScriptEngine()).compile(read));
        assertThat(e.getMessage(), containsString("Test IOE"));
    }
}
