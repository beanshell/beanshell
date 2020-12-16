package bsh;

import bsh.classpath.ClassManagerImpl;

import java.io.*;
import java.util.Map;

/**
 * With this class the script source is only parsed once and the resulting AST is used for
 * {@link #invoke(java.util.Map) every invocation}. This class is designed to be thread-safe.  
 */
public class PreparsedScript {

	private final BshMethod _method;
	private final Interpreter _interpreter;


	public PreparsedScript(final String source) throws EvalError {
		this(source, getDefaultClassLoader());
	}


	private static ClassLoader getDefaultClassLoader() {
		ClassLoader cl = null;
		try {
			cl = Thread.currentThread().getContextClassLoader();
		} catch (final SecurityException e) {
			// ignore
		}
		if (cl == null) {
			cl = PreparsedScript.class.getClassLoader();
		}
		if (cl != null) {
			return cl;
		}
		return ClassLoader.getSystemClassLoader();
	}


	public PreparsedScript(final String source, final ClassLoader classLoader) throws EvalError {
		final ClassManagerImpl classManager = new ClassManagerImpl();
		classManager.setClassLoader(classLoader);
		final NameSpace nameSpace = new NameSpace(classManager, "global");
		_interpreter = new Interpreter(new StringReader(""), System.out, System.err, false, nameSpace, null, null);
		try {
			final This callable = (This) _interpreter.eval("__execute() { " + source + "\n" + "}\n" + "return this;");
			_method = callable.getNameSpace().getMethod("__execute", new Class[0], false);
		} catch (final UtilEvalError e) {
			throw new IllegalStateException(e);
		}
	}


	public Object invoke(final Map<String, ?> context) throws EvalError {
		final NameSpace nameSpace = new NameSpace(_interpreter.getClassManager(), "BeanshellExecutable");
		nameSpace.setParent(_interpreter.getNameSpace());
		final BshMethod method = new BshMethod(_method.getName(), _method.getReturnType(), _method.getParameterNames(), _method.getParameterTypes(), _method.methodBody, nameSpace, _method.getModifiers());
		for (final Map.Entry<String, ?> entry : context.entrySet()) {
			try {
				final Object value = entry.getValue();
				nameSpace.setVariable(entry.getKey(), value != null ? value : Primitive.NULL, false);
			} catch (final UtilEvalError e) {
				throw new EvalError("cannot set variable '" + entry.getKey() + '\'', null, null, e);
			}
		}
		final Object result = method.invoke(new Object[0], _interpreter);
		if (result instanceof Primitive) {
			if (( (Primitive) result).getType() == Void.TYPE) {
				return null;
			}
			return ( (Primitive) result).getValue();
		}
		return result;
	}


	public void setOut(final PrintStream value) {
		_interpreter.setOut(value);
	}


	public void setErr(final PrintStream value) {
		_interpreter.setErr(value);
	}

}
