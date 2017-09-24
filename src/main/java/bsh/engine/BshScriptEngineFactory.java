/*
 *
 */
package bsh.engine;

import java.util.Arrays;
import java.util.List;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;

/**
 * A factory for creating BshScriptEngine objects.
 */
public class BshScriptEngineFactory implements ScriptEngineFactory {
    // Begin impl ScriptEnginInfo

    /** The extensions. */
    final List<String> extensions = Arrays.asList("bsh", "java");
    /** The mime types. */
    final List<String> mimeTypes = Arrays.asList("application/x-beanshell",
            "application/x-bsh", "application/x-java-source");
    /** The names. */
    final List<String> names = Arrays.asList("beanshell", "bsh", "java");

    /** {@inheritDoc} */
    public String getEngineName() {
        return "BeanShell Engine";
    }

    /** {@inheritDoc} */
    public String getEngineVersion() {
        return "1.0";
    }

    /** {@inheritDoc} */
    public List<String> getExtensions() {
        return this.extensions;
    }

    /** {@inheritDoc} */
    public List<String> getMimeTypes() {
        return this.mimeTypes;
    }

    /** {@inheritDoc} */
    public List<String> getNames() {
        return this.names;
    }

    /** {@inheritDoc} */
    public String getLanguageName() {
        return "BeanShell";
    }

    /** {@inheritDoc} */
    public String getLanguageVersion() {
        return bsh.Interpreter.VERSION + "";
    }

    /** {@inheritDoc} */
    public Object getParameter(final String param) {
        if (param.equals(ScriptEngine.ENGINE))
            return this.getEngineName();
        if (param.equals(ScriptEngine.ENGINE_VERSION))
            return this.getEngineVersion();
        if (param.equals(ScriptEngine.NAME))
            return this.getEngineName();
        if (param.equals(ScriptEngine.LANGUAGE))
            return this.getLanguageName();
        if (param.equals(ScriptEngine.LANGUAGE_VERSION))
            return this.getLanguageVersion();
        if (param.equals("THREADING"))
            return "MULTITHREADED";
        return null;
    }

    /** {@inheritDoc} */
    public String getMethodCallSyntax(final String objectName,
            final String methodName, final String... args) {
        // Note: this is very close to the bsh.StringUtil.methodString()
        // method, which constructs a method signature from arg *types*. Maybe
        // combine these later.
        final StringBuffer sb = new StringBuffer();
        if (objectName != null)
            sb.append(objectName + ".");
        sb.append(methodName + "(");
        if (args.length > 0)
            sb.append(" ");
        for (int i = 0; i < args.length; i++)
            sb.append((args[i] == null ? "null" : args[i])
                    + (i < args.length - 1 ? ", " : " "));
        sb.append(")");
        return sb.toString();
    }

    /** {@inheritDoc} */
    public String getOutputStatement(final String message) {
        return "print(\"" + message + "\");";
    }

    /** {@inheritDoc} */
    public String getProgram(final String... statements) {
        final StringBuffer sb = new StringBuffer();
        for (int i = 0; i < statements.length; i++) {
            sb.append(statements[i]);
            if (!statements[i].endsWith(";"))
                sb.append(";");
            sb.append("\n");
        }
        return sb.toString();
    }
    // End impl ScriptEngineInfo

    // Begin impl ScriptEngineFactory
    /** {@inheritDoc} */
    public ScriptEngine getScriptEngine() {
        return new BshScriptEngine();
    }
    // End impl ScriptEngineFactory
}
