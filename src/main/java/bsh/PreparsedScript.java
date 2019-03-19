package bsh;

import java.io.PrintStream;
import java.io.StringReader;
import java.util.Map;

import bsh.classpath.ClassManagerImpl;

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

    public PreparsedScript(final String source, final ClassLoader classLoader) throws EvalError {
        final ClassManagerImpl classManager = new ClassManagerImpl();
        classManager.setClassLoader(classLoader);
        final NameSpace nameSpace = new NameSpace("global", classManager);
        _interpreter = new Interpreter(new StringReader(""), System.out, System.err, false, nameSpace, null, null);
        try {
            final This callable = (This) _interpreter.eval("__execute() { " + source + "\n" + "}\n" + "return this;");
            _method = callable.getNameSpace().getMethod("__execute", Reflect.ZERO_TYPES, false);
        } catch (final UtilEvalError e) {
            throw new IllegalStateException(e);
        }
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

    public Object invoke(final Map<String, ?> context) throws EvalError {
        final NameSpace nameSpace = new NameSpace("BeanshellExecutable", _interpreter.getClassManager());
        nameSpace.setParent(_interpreter.getNameSpace());
        final BshMethod method = new BshMethod(_method.getName(), _method.getReturnType(),
                _method.getParameterNames(), _method.getParameterTypes(), _method.getParameterModifiers(),
                _method.methodBody, nameSpace, _method.getModifiers());
        for (final Map.Entry<String, ?> entry : context.entrySet()) {
            try {
                final Object value = entry.getValue();
                nameSpace.setVariable(entry.getKey(), value != null ? value : Primitive.NULL, false);
            } catch (final UtilEvalError e) {
                throw new EvalError("cannot set variable '" + entry.getKey() + '\'', null, null, e);
            }
        }
        final Object result = method.invoke(Reflect.ZERO_ARGS, _interpreter);
        if ( Types.getType(result) == Void.TYPE )
            return null;
        return Primitive.unwrap(result);
    }


    public void setOut(final PrintStream value) {
        _interpreter.setOut(value);
    }


    public void setErr(final PrintStream value) {
        _interpreter.setErr(value);
    }

}
