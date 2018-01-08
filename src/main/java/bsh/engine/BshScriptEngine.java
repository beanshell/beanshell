package bsh.engine;

import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.script.*;
import bsh.*;
import static javax.script.ScriptContext.*;

/*
	Notes
	This engine supports open-ended pluggable scriptcontexts
*/
public class BshScriptEngine extends AbstractScriptEngine
	implements Compilable, Invocable
{
	// The BeanShell global namespace for the interpreter is stored in the
	// engine scope map under this key.
	static final String engineNameSpaceKey = "org_beanshell_engine_namespace";

	private BshScriptEngineFactory factory;
	private bsh.Interpreter interpreter;

	public BshScriptEngine() {
		this( null );
	}

	public BshScriptEngine( BshScriptEngineFactory factory ) 
	{
		this.factory = factory;
		getInterpreter(); // go ahead and prime the interpreter now
	}

	protected Interpreter getInterpreter()
	{
		if ( interpreter == null ) {
			this.interpreter = new bsh.Interpreter();
			interpreter.setNameSpace(null); // should always be set by context
		}

		return interpreter;
	}

	public Object eval( String script, ScriptContext scriptContext )
		throws ScriptException
	{
		return evalSource( script, scriptContext );
	}

	public Object eval( Reader reader, ScriptContext scriptContext )
		throws ScriptException
	{
		return evalSource( reader, scriptContext );
	}

	/*
		This is the primary implementation method.
		We respect the String/Reader difference here in BeanShell because
		BeanShell will do a few extra things in the string case... e.g.
		tack on a trailing ";" semicolon if necessary.
	*/
	private Object evalSource( Object source, ScriptContext scriptContext )
		throws ScriptException
	{
		bsh.NameSpace contextNameSpace = getEngineNameSpace( scriptContext );
		Interpreter bsh = getInterpreter();
		bsh.setNameSpace( contextNameSpace );

		// This is a big hack, convert writer to PrintStream
		bsh.setOut( new PrintStream(
			new WriterOutputStream( scriptContext.getWriter() ) ) );
		bsh.setErr( new PrintStream(
			new WriterOutputStream( scriptContext.getErrorWriter() ) ) );

		try {
			if ( source instanceof Reader )
				return bsh.eval( (Reader) source );
			else
				return bsh.eval( (String) source );
		} catch ( ParseException e ) {
			// explicit parsing error
			throw new ScriptException(
				e.toString(), e.getErrorSourceFile(), e.getErrorLineNumber() );
		} catch ( TargetError e ) {
			// The script threw an application level exception
			// set it as the cause ?
			ScriptException se = new ScriptException(
				e.toString(), e.getErrorSourceFile(), e.getErrorLineNumber() );
			se.initCause( e.getTarget() );
			throw se;
		} catch ( EvalError e ) {
			// The script couldn't be evaluated properly
			throw new ScriptException(
				e.toString(), e.getErrorSourceFile(), e.getErrorLineNumber() );
		} catch ( InterpreterError e ) {
			// The interpreter had a fatal problem
			throw new ScriptException( e.toString() );
		}
	}



	/*
		Check the context for an existing global namespace embedded
		in the script context engine scope.  If none exists, ininitialize the
		context with one.
	*/
	private static NameSpace getEngineNameSpace( ScriptContext scriptContext )
	{
		NameSpace ns = (NameSpace)scriptContext.getAttribute(
			engineNameSpaceKey, ENGINE_SCOPE );

		if ( ns == null )
		{
			// Create a global namespace for the interpreter
			Map engineView = new ScriptContextEngineView( scriptContext );
			ns = new ExternalNameSpace(
				null/*parent*/, "javax_script_context", engineView );

			scriptContext.setAttribute( engineNameSpaceKey, ns, ENGINE_SCOPE );
		}

		return ns;
	}

	public Bindings createBindings()
	{
		return new SimpleBindings();
	}

    public ScriptEngineFactory getFactory()
	{
		if ( factory == null )
			factory = new BshScriptEngineFactory();
		return factory;
	}

    /**
	 * Compiles the script (source represented as a {@code String}) for later
	 * execution.
	 *
	 * @param script The source of the script, represented as a {@code String}.
	 * @return An subclass of {@code CompiledScript} to be executed later
	 *         using one of the {@code eval} methods of {@code CompiledScript}.
	 * @throws ScriptException	  if compilation fails.
	 * @throws NullPointerException if the argument is null.
	 */

	public CompiledScript compile(String script) throws ScriptException {
		try {
			final PreparsedScript preparsed = new PreparsedScript(script);
			return new CompiledScript() {

				@Override
				public Object eval(ScriptContext context) throws ScriptException {
					final HashMap<String, Object> map = new HashMap<String, Object>();
					final List<Integer> scopes = new ArrayList<Integer>(context.getScopes());
					Collections.sort(scopes); // lowest scope at first pos
					Collections.reverse(scopes); // highest scope at first pos
					for (final Integer scope : scopes) {
						map.putAll(context.getBindings(scope));
					}
					preparsed.setOut(toPrintStream(context.getWriter()));
					preparsed.setErr(toPrintStream(context.getErrorWriter()));
					try {
						return preparsed.invoke(map);
					} catch (final EvalError e) {
						throw constructScriptException(e);
					}
				}


				@Override
				public ScriptEngine getEngine() {
					return BshScriptEngine.this;
				}

			};
		} catch (final EvalError e) {
			throw constructScriptException(e);
		}
	}


	private ScriptException constructScriptException(final EvalError e) {
		return new ScriptException(e.getMessage(), e.getErrorSourceFile(), e.getErrorLineNumber());
	}


	private static String convertToString(Reader reader) throws IOException {
		final StringBuffer buffer = new StringBuffer(64);
		char[] cb = new char[64];
		int len;
		while ((len = reader.read(cb)) != -1) {
			buffer.append(cb, 0, len);
		}
		return buffer.toString();
	}

	
	/**
	 * Compiles the script (source read from {@code Reader}) for later
	 * execution.  Functionality is identical to {@code compile(String)} other
	 * than the way in which the source is passed.
	 *
	 * @param script The reader from which the script source is obtained.
	 * @return An implementation of {@code CompiledScript} to be executed
	 *         later using one of its {@code eval} methods of
	 *         {@code CompiledScript}.
	 * @throws ScriptException	  if compilation fails.
	 * @throws NullPointerException if argument is null.
	 */
	public CompiledScript compile(Reader script) throws ScriptException {
		try {
			return compile(convertToString(script));
		} catch (IOException e) {
			throw new ScriptException(e);
		}
	}

	/**
	 * Calls a procedure compiled during a previous script execution, which is
	 * retained in the state of the <code>ScriptEngine<code>.
	 *
	 * @param name The name of the procedure to be called.
	 * @param thiz If the procedure is a member  of a class defined in the script
	 * and thiz is an instance of that class returned by a previous execution or
	 * invocation, the named method is called through that instance. If classes are
	 * not supported in the scripting language or if the procedure is not a member
	 * function of any class, the argument must be <code>null</code>.
	 * @param args Arguments to pass to the procedure.  The rules for converting
	 * the arguments to scripting variables are implementation-specific.
	 *
	 * @return The value returned by the procedure.  The rules for converting the
	 *         scripting variable returned by the procedure to a Java Object are
	 *         implementation-specific.
	 *
	 * @throws javax.script.ScriptException if an error occurrs during invocation
	 * of the method.
	 * @throws NoSuchMethodException if method with given name or matching argument
	 * types cannot be found.
	 * @throws NullPointerException if method name is null.
	 */
	public Object invokeMethod( Object thiz, String name, Object... args )
        throws ScriptException, NoSuchMethodException
	{
		if ( ! (thiz instanceof bsh.This) )
			throw new ScriptException( "Illegal objec type: " +thiz.getClass() );

		bsh.This bshObject = (bsh.This)thiz;

		try {
			return bshObject.invokeMethod( name, args );
		} catch ( ParseException e ) {
			// explicit parsing error
			throw new ScriptException(
				e.toString(), e.getErrorSourceFile(), e.getErrorLineNumber() );
		} catch ( TargetError e ) {
			// The script threw an application level exception
			// set it as the cause ?
			ScriptException se = new ScriptException(
				e.toString(), e.getErrorSourceFile(), e.getErrorLineNumber() );
			se.initCause( e.getTarget() );
			throw se;
		} catch ( EvalError e ) {
			// The script couldn't be evaluated properly
			throw new ScriptException(
				e.toString(), e.getErrorSourceFile(), e.getErrorLineNumber() );
		} catch ( InterpreterError e ) {
			// The interpreter had a fatal problem
			throw new ScriptException( e.toString() );
		}
	}

	/**
	 * Same as invoke(Object, String, Object...) with <code>null</code> as the
	 * first argument.  Used to call top-level procedures defined in scripts.
	 *
	 * @param args Arguments to pass to the procedure
	 *
	 * @return The value returned by the procedure
	 *
	 * @throws javax.script.ScriptException if an error occurrs during invocation
	 * of the method.
	 * @throws NoSuchMethodException if method with given name or matching
	 * argument types cannot be found.
	 * @throws NullPointerException if method name is null.
	 */
	public Object invokeFunction( String name, Object... args )
		throws ScriptException, NoSuchMethodException
	{
		return invokeMethod( getGlobal(), name, args );
	}

    /**
	 * Returns an implementation of an interface using procedures compiled in the
	 * interpreter. The methods of the interface may be implemented using the
	 * <code>invoke</code> method.
	 *
	 * @param clasz The <code>Class</code> object of the interface to return.
	 *
	 * @return An instance of requested interface - null if the requested interface
	 *         is unavailable, i. e. if compiled methods in the
	 *         <code>ScriptEngine</code> cannot be found matching the ones in the
	 *         requested interface.
	 *
	 * @throws IllegalArgumentException if the specified <code>Class</code> object
	 * does not exist or is not an interface.
	 */
	public <T> T getInterface( Class<T> clasz )
	{
		try {
			return (T) getGlobal().getInterface( clasz );
		} catch ( UtilEvalError utilEvalError ) {
			utilEvalError.printStackTrace();
			return null;
		}
	}

	/**
	 * Returns an implementation of an interface using member functions of a
	 * scripting object compiled in the interpreter. The methods of the interface
	 * may be implemented using invoke(Object, String, Object...) method.
	 *
	 * @param thiz The scripting object whose member functions are used to
	 * implement the methods of the interface.
	 * @param clasz The <code>Class</code> object of the interface to return.
	 *
	 * @return An instance of requested interface - null if the requested
	 *         interface is unavailable, i. e. if compiled methods in the
	 *         <code>ScriptEngine</code> cannot be found matching the ones in the
	 *         requested interface.
	 *
	 * @throws IllegalArgumentException if the specified <code>Class</code> object
	 * does not exist or is not an interface, or if the specified Object is null
	 * or does not represent a scripting object.
	 */
	public <T> T getInterface( Object thiz, Class<T> clasz )
	{
		if ( !(thiz instanceof bsh.This) )
			throw new IllegalArgumentException(
				"invalid object type: "+thiz.getClass() );

		try {
			bsh.This bshThis = (bsh.This)thiz;
			return (T) bshThis.getInterface( clasz );
		} catch ( UtilEvalError utilEvalError ) {
			utilEvalError.printStackTrace( System.err );
			return null;
		}
	}

	private bsh.This getGlobal()
	{
		// requires 2.0b5 to make getThis() public
		return getEngineNameSpace( getContext() ).getThis( getInterpreter() );
	}

	/*
		This is a total hack.  We need to introduce a writer to the
		Interpreter.
	*/
	class WriterOutputStream extends OutputStream
	{
		Writer writer;
		WriterOutputStream( Writer writer )
		{
			this.writer = writer;
		}

		public void write( int b ) throws IOException
		{
			writer.write(b);
		}

		public void flush() throws IOException
		{
			writer.flush();
		}

		public void close() throws IOException
		{
			writer.close();
		}
	}

	private PrintStream toPrintStream(final Writer writer) {
		// This is a big hack, convert writer to PrintStream
		return new PrintStream(new WriterOutputStream(writer));
	}
}
