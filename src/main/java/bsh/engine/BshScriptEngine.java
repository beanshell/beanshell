package bsh.engine;

import static javax.script.ScriptContext.ENGINE_SCOPE;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.Reader;
import java.io.StringReader;
import java.io.Writer;
import java.util.Map;

import javax.script.AbstractScriptEngine;
import javax.script.Bindings;
import javax.script.Compilable;
import javax.script.CompiledScript;
import javax.script.Invocable;
import javax.script.ScriptContext;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptException;
import javax.script.SimpleBindings;

import bsh.EvalError;
import bsh.ExternalNameSpace;
import bsh.Interpreter;
import bsh.InterpreterError;
import bsh.NameSpace;
import bsh.ParseException;
import bsh.TargetError;
import bsh.UtilEvalError;

/**
 * The Class BshScriptEngine.
 *
 * Notes
 * This engine supports open-ended pluggable scriptcontexts
 */
public class BshScriptEngine extends AbstractScriptEngine
        implements Compilable, Invocable {

    // The BeanShell global namespace for the interpreter is stored in the
    /** The Constant engineNameSpaceKey. */
    // engine scope map under this key.
    static final String engineNameSpaceKey = "org_beanshell_engine_namespace";
    /** The factory. */
    private BshScriptEngineFactory factory;
    /** The interpreter. */
    private bsh.Interpreter interpreter;

    /**
     * Instantiates a new bsh script engine.
     */
    public BshScriptEngine() {
        this(null);
    }

    /**
     * Instantiates a new bsh script engine.
     *
     * @param factory
     *            the factory
     */
    public BshScriptEngine(final BshScriptEngineFactory factory) {
        this.factory = factory;
        this.getInterpreter(); // go ahead and prime the interpreter now
    }

    /**
     * Gets the interpreter.
     *
     * @return the interpreter
     */
    protected Interpreter getInterpreter() {
        if (this.interpreter == null) {
            this.interpreter = new bsh.Interpreter();
            this.interpreter.setNameSpace(null); // should always be set by
                                                 // context
        }
        return this.interpreter;
    }

    /** {@inheritDoc} */
    public Object eval(final String script, final ScriptContext scriptContext)
            throws ScriptException {
        return this.evalSource(script, scriptContext);
    }

    /** {@inheritDoc} */
    public Object eval(final Reader reader, final ScriptContext scriptContext)
            throws ScriptException {
        return this.evalSource(reader, scriptContext);
    }

    /**
     * Eval source.
     *
     * @param source
     *            the source
     * @param scriptContext
     *            the script context
     * @return the object
     * @throws ScriptException
     *             the script exception
     *
     * This is the primary implementation method.
     * We respect the String/Reader difference here in BeanShell because
     * BeanShell will do a few extra things in the string case... e.g.
     * tack on a trailing ";" semicolon if necessary.
     */
    private Object evalSource(final Object source,
            final ScriptContext scriptContext) throws ScriptException {
        final bsh.NameSpace contextNameSpace = getEngineNameSpace(
                scriptContext);
        final Interpreter bsh = this.getInterpreter();
        bsh.setNameSpace(contextNameSpace);
        // This is a big hack, convert writer to PrintStream
        bsh.setOut(new PrintStream(
                new WriterOutputStream(scriptContext.getWriter())));
        bsh.setErr(new PrintStream(
                new WriterOutputStream(scriptContext.getErrorWriter())));
        try {
            if (source instanceof Reader)
                return bsh.eval((Reader) source);
            else
                return bsh.eval((String) source);
        } catch (final ParseException e) {
            // explicit parsing error
            throw new ScriptException(e.toString(), e.getErrorSourceFile(),
                    e.getErrorLineNumber());
        } catch (final TargetError e) {
            // The script threw an application level exception
            // set it as the cause ?
            final ScriptException se = new ScriptException(e.toString(),
                    e.getErrorSourceFile(), e.getErrorLineNumber());
            se.initCause(e.getTarget());
            throw se;
        } catch (final EvalError e) {
            // The script couldn't be evaluated properly
            throw new ScriptException(e.toString(), e.getErrorSourceFile(),
                    e.getErrorLineNumber());
        } catch (final InterpreterError e) {
            // The interpreter had a fatal problem
            throw new ScriptException(e.toString());
        }
    }

    /**
     * Gets the engine name space.
     *
     * @param scriptContext
     *            the script context
     * @return the engine name space
     *
     * Check the context for an existing global namespace embedded
     * in the script context engine scope. If none exists, ininitialize the
     * context with one.
     */
    private static NameSpace getEngineNameSpace(
            final ScriptContext scriptContext) {
        NameSpace ns = (NameSpace) scriptContext
                .getAttribute(engineNameSpaceKey, ENGINE_SCOPE);
        if (ns == null) {
            // Create a global namespace for the interpreter
            final Map engineView = new ScriptContextEngineView(scriptContext);
            ns = new ExternalNameSpace(null/* parent */, "javax_script_context",
                    engineView);
            scriptContext.setAttribute(engineNameSpaceKey, ns, ENGINE_SCOPE);
        }
        return ns;
    }

    /** {@inheritDoc} */
    public Bindings createBindings() {
        return new SimpleBindings();
    }

    /** {@inheritDoc} */
    public ScriptEngineFactory getFactory() {
        if (this.factory == null)
            this.factory = new BshScriptEngineFactory();
        return this.factory;
    }

    /**
     * Compiles the script (source represented as a <code>String</code>) for
     * later
     * execution.
     *
     * @param script
     *            The source of the script, represented as a
     *            <code>String</code>.
     *
     * @return An subclass of <code>CompiledScript</code> to be executed later
     *         using one of the <code>eval</code> methods of
     *         <code>CompiledScript</code>.
     *
     * @throws ScriptException
     *             if compilation fails.
     * @throws NullPointerException
     *             if the argument is null.
     */
    public CompiledScript compile(final String script) throws ScriptException {
        return this.compile(new StringReader(script));
    }

    /**
     * Compiles the script (source read from <code>Reader</code>) for later
     * execution. Functionality is identical to <code>compile(String)</code>
     * other
     * than the way in which the source is passed.
     *
     * @param script
     *            The reader from which the script source is obtained.
     *
     * @return An implementation of <code>CompiledScript</code> to be executed
     *         later using one of its <code>eval</code> methods of
     *         <code>CompiledScript</code>.
     *
     * @throws ScriptException
     *             if compilation fails.
     * @throws NullPointerException
     *             if argument is null.
     */
    public CompiledScript compile(final Reader script) throws ScriptException {
        // todo
        throw new Error("unimplemented");
    }

    /**
     * Calls a procedure compiled during a previous script execution, which is
     * retained in the state of the <code>ScriptEngine</code>.
     *
     * &#64;param name The name of the procedure to be called.
     * &#64;param thiz If the procedure is a member  of a class defined in the script
     * and thiz is an instance of that class returned by a previous execution or
     * invocation, the named method is called through that instance. If classes are
     * not supported in the scripting language or if the procedure is not a member
     * function of any class, the argument must be <code>null</code>.
     *
     * @param thiz
     *            the thiz
     * @param name
     *            the name
     * @param args
     *            Arguments to pass to the procedure. The rules for converting
     *            the arguments to scripting variables are
     *            implementation-specific.
     * @return The value returned by the procedure. The rules for converting the
     *         scripting variable returned by the procedure to a Java Object are
     *         implementation-specific.
     * @throws ScriptException
     *             the script exception
     * @throws NoSuchMethodException
     *             if method with given name or matching argument
     *             types cannot be found.
     * @throws NullPointerException
     *             if method name is null.
     */
    public Object invokeMethod(final Object thiz, final String name,
            final Object... args)
            throws ScriptException, NoSuchMethodException {
        if (!(thiz instanceof bsh.This))
            throw new ScriptException("Illegal objec type: " + thiz.getClass());
        final bsh.This bshObject = (bsh.This) thiz;
        try {
            return bshObject.invokeMethod(name, args);
        } catch (final ParseException e) {
            // explicit parsing error
            throw new ScriptException(e.toString(), e.getErrorSourceFile(),
                    e.getErrorLineNumber());
        } catch (final TargetError e) {
            // The script threw an application level exception
            // set it as the cause ?
            final ScriptException se = new ScriptException(e.toString(),
                    e.getErrorSourceFile(), e.getErrorLineNumber());
            se.initCause(e.getTarget());
            throw se;
        } catch (final EvalError e) {
            // The script couldn't be evaluated properly
            throw new ScriptException(e.toString(), e.getErrorSourceFile(),
                    e.getErrorLineNumber());
        } catch (final InterpreterError e) {
            // The interpreter had a fatal problem
            throw new ScriptException(e.toString());
        }
    }

    /**
     * Same as invoke(Object, String, Object...) with <code>null</code> as the
     * first argument. Used to call top-level procedures defined in scripts.
     *
     * @param name
     *            the name
     * @param args
     *            Arguments to pass to the procedure
     * @return The value returned by the procedure
     * @throws ScriptException
     *             the script exception
     * @throws NoSuchMethodException
     *             if method with given name or matching
     *             argument types cannot be found.
     * @throws NullPointerException
     *             if method name is null.
     */
    public Object invokeFunction(final String name, final Object... args)
            throws ScriptException, NoSuchMethodException {
        return this.invokeMethod(this.getGlobal(), name, args);
    }

    /**
     * Returns an implementation of an interface using procedures compiled in
     * the
     * interpreter. The methods of the interface may be implemented using the
     * <code>invoke</code> method.
     *
     * @param <T>
     *            the generic type
     * @param clasz
     *            The <code>Class</code> object of the interface to return.
     * @return An instance of requested interface - null if the requested
     *         interface
     *         is unavailable, i. e. if compiled methods in the
     *         <code>ScriptEngine</code> cannot be found matching the ones in
     *         the
     *         requested interface.
     * @throws IllegalArgumentException
     *             if the specified <code>Class</code> object
     *             does not exist or is not an interface.
     */
    public <T> T getInterface(final Class<T> clasz) {
        try {
            return (T) this.getGlobal().getInterface(clasz);
        } catch (final UtilEvalError utilEvalError) {
            utilEvalError.printStackTrace();
            return null;
        }
    }

    /**
     * Returns an implementation of an interface using member functions of a
     * scripting object compiled in the interpreter. The methods of the
     * interface
     * may be implemented using invoke(Object, String, Object...) method.
     *
     * @param <T>
     *            the generic type
     * @param thiz
     *            The scripting object whose member functions are used to
     *            implement the methods of the interface.
     * @param clasz
     *            The <code>Class</code> object of the interface to return.
     * @return An instance of requested interface - null if the requested
     *         interface is unavailable, i. e. if compiled methods in the
     *         <code>ScriptEngine</code> cannot be found matching the ones in
     *         the
     *         requested interface.
     * @throws IllegalArgumentException
     *             if the specified <code>Class</code> object
     *             does not exist or is not an interface, or if the specified
     *             Object is null
     *             or does not represent a scripting object.
     */
    public <T> T getInterface(final Object thiz, final Class<T> clasz) {
        if (!(thiz instanceof bsh.This))
            throw new IllegalArgumentException(
                    "invalid object type: " + thiz.getClass());
        try {
            final bsh.This bshThis = (bsh.This) thiz;
            return (T) bshThis.getInterface(clasz);
        } catch (final UtilEvalError utilEvalError) {
            utilEvalError.printStackTrace(System.err);
            return null;
        }
    }

    /**
     * Gets the global.
     *
     * @return the global
     */
    private bsh.This getGlobal() {
        // requires 2.0b5 to make getThis() public
        return getEngineNameSpace(this.getContext())
                .getThis(this.getInterpreter());
    }

    /**
     * The Class WriterOutputStream.
     *
     * This is a total hack. We need to introduce a writer to the
     * Interpreter.
     */
    class WriterOutputStream extends OutputStream {

        /** The writer. */
        Writer writer;

        /**
         * Instantiates a new writer output stream.
         *
         * @param writer
         *            the writer
         */
        WriterOutputStream(final Writer writer) {
            this.writer = writer;
        }

        /** {@inheritDoc} */
        @Override
        public void write(final int b) throws IOException {
            this.writer.write(b);
        }

        /** {@inheritDoc} */
        @Override
        public void flush() throws IOException {
            this.writer.flush();
        }

        /** {@inheritDoc} */
        @Override
        public void close() throws IOException {
            this.writer.close();
        }
    }
}
