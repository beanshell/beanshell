package bsh.util;

/*
    This file is associated with the BeanShell Java Scripting language
    distribution (http://www.beanshell.org/).

    This file is hereby placed into the public domain...  You may copy,
    modify, and redistribute it without restriction.
*/
import java.util.Vector;

import org.apache.bsf.BSFDeclaredBean;
import org.apache.bsf.BSFException;
import org.apache.bsf.BSFManager;
import org.apache.bsf.util.BSFEngineImpl;

import bsh.EvalError;
import bsh.Interpreter;
import bsh.InterpreterError;
import bsh.Primitive;
import bsh.TargetError;

/**
 * This is the BeanShell adapter for Apache Bean Scripting Framework 2.x.
 * <p>
 * It is an implementation of the BSFEngine class, allowing BSF aware
 * applications to use BeanShell as a scripting language.
 * <p>
 * I believe this implementation is complete (with some hesitation about the
 * the usefullness of the compileXXX() style methods - provided by the base
 * utility class).
 *
 * See http://www.beanshell.org/manual/bsf.html for some more info.
 * <p/>
 *
 * @author Pat Niemeyer
 */
public class BeanShellBSFEngine extends BSFEngineImpl {

    /** The interpreter. */
    Interpreter interpreter;
    /** The installed apply method. */
    boolean installedApplyMethod;

    /** {@inheritDoc} */
    @Override
    public void initialize(final BSFManager mgr, final String lang,
            final Vector declaredBeans) throws BSFException {
        super.initialize(mgr, lang, declaredBeans);
        this.interpreter = new Interpreter();
        // declare the bsf manager for callbacks, etc.
        try {
            this.interpreter.set("bsf", mgr);
        } catch (final EvalError e) {
            throw new BSFException("bsh internal error: " + e.toString());
        }
        for (int i = 0; i < declaredBeans.size(); i++) {
            final BSFDeclaredBean bean = (BSFDeclaredBean) declaredBeans.get(i);
            this.declareBean(bean);
        }
    }

    /**
     * Sets the debug.
     *
     * @param debug
     *            the new debug
     */
    public void setDebug(final boolean debug) {
        Interpreter.DEBUG = debug;
    }

    /**
     * Invoke method name on the specified bsh scripted object.
     * The object may be null to indicate the global namespace of the
     * interpreter.
     *
     * @param object
     *            may be null for the global namespace.
     * @param name
     *            the name
     * @param args
     *            the args
     * @return the object
     * @throws BSFException
     *             the BSF exception
     */
    public Object call(Object object, final String name, final Object[] args)
            throws BSFException {
        /*
         * If object is null use the interpreter's global scope.
         */
        if (object == null)
            try {
                object = this.interpreter.get("global");
            } catch (final EvalError e) {
                throw new BSFException("bsh internal error: " + e.toString());
            }
        if (object instanceof bsh.This)
            try {
                final Object value = ((bsh.This) object).invokeMethod(name,
                        args);
                return Primitive.unwrap(value);
            } catch (final InterpreterError e) {
                throw new BSFException(
                        "BeanShell interpreter internal error: " + e);
            } catch (final TargetError e2) {
                throw new BSFException(
                        "The application script threw an exception: "
                                + e2.getTarget());
            } catch (final EvalError e3) {
                throw new BSFException("BeanShell script error: " + e3);
            }
        else
            throw new BSFException(
                    "Cannot invoke method: " + name + ". Object: " + object
                            + " is not a BeanShell scripted object.");
    }

    /**
     * A helper BeanShell method that implements the anonymous method apply
     * proposed by BSF. Note that the script below could use the standard
     * bsh eval() method to set the variables and apply the text, however
     * then I'd have to escape quotes, etc.
     */
    static final String bsfApplyMethod = "_bsfApply(_bsfNames, _bsfArgs, _bsfText) {"
            + "for(i=0;i<_bsfNames.length;i++)"
            + "this.namespace.setVariable(_bsfNames[i], _bsfArgs[i],false);"
            + "return this.interpreter.eval(_bsfText, this.namespace);" + "}";

    /**
     * This is an implementation of the BSF apply() method.
     * It exectutes the funcBody text in an "anonymous" method call with
     * arguments.
     *
     * @param source
     *            the source
     * @param lineNo
     *            the line no
     * @param columnNo
     *            the column no
     * @param funcBody
     *            the func body
     * @param namesVec
     *            the names vec
     * @param argsVec
     *            the args vec
     * @return the object
     * @throws BSFException
     *             the BSF exception
     *
     * Note: the apply() method may be supported directly in BeanShell in an
     * upcoming release and would not require special support here.
     */
    @Override
    public Object apply(final String source, final int lineNo,
            final int columnNo, final Object funcBody, final Vector namesVec,
            final Vector argsVec) throws BSFException {
        if (namesVec.size() != argsVec.size())
            throw new BSFException("number of params/names mismatch");
        if (!(funcBody instanceof String))
            throw new BSFException("apply: functino body must be a string");
        final String[] names = new String[namesVec.size()];
        namesVec.copyInto(names);
        final Object[] args = new Object[argsVec.size()];
        argsVec.copyInto(args);
        try {
            if (!this.installedApplyMethod) {
                this.interpreter.eval(bsfApplyMethod);
                this.installedApplyMethod = true;
            }
            final bsh.This global = (bsh.This) this.interpreter.get("global");
            final Object value = global.invokeMethod("_bsfApply",
                    new Object[] {names, args, (String) funcBody});
            return Primitive.unwrap(value);
        } catch (final InterpreterError e) {
            throw new BSFException("BeanShell interpreter internal error: " + e
                    + this.sourceInfo(source, lineNo, columnNo));
        } catch (final TargetError e2) {
            throw new BSFException("The application script threw an exception: "
                    + e2.getTarget()
                    + this.sourceInfo(source, lineNo, columnNo));
        } catch (final EvalError e3) {
            throw new BSFException("BeanShell script error: " + e3
                    + this.sourceInfo(source, lineNo, columnNo));
        }
    }

    /** {@inheritDoc} */
    public Object eval(final String source, final int lineNo,
            final int columnNo, final Object expr) throws BSFException {
        if (!(expr instanceof String))
            throw new BSFException("BeanShell expression must be a string");
        try {
            return this.interpreter.eval((String) expr);
        } catch (final InterpreterError e) {
            throw new BSFException("BeanShell interpreter internal error: " + e
                    + this.sourceInfo(source, lineNo, columnNo));
        } catch (final TargetError e2) {
            throw new BSFException("The application script threw an exception: "
                    + e2.getTarget()
                    + this.sourceInfo(source, lineNo, columnNo));
        } catch (final EvalError e3) {
            throw new BSFException("BeanShell script error: " + e3
                    + this.sourceInfo(source, lineNo, columnNo));
        }
    }

    /** {@inheritDoc} */
    @Override
    public void exec(final String source, final int lineNo, final int columnNo,
            final Object script) throws BSFException {
        this.eval(source, lineNo, columnNo, script);
    }

    /*
     * I don't quite understand these compile methods. The default impl
     * will use the CodeBuffer utility to produce an example (Test) class that
     * turns around and invokes the BSF Manager to call the script again.
     * I assume a statically compiled language would return a real
     * implementation
     * class adapter here? But in source code form? Would't it be more likely
     * to generate bytecode?
     * And shouldn't a non-compiled language simply return a standard
     * precompiled adapter to itself? The indirection of building a source
     * class to call the scripting engine (possibly through the interpreter)
     * seems kind of silly.
     ** {@inheritDoc} */
    /*
     * public void compileApply (String source, int lineNo, int columnNo,
     * Object funcBody, Vector paramNames, Vector arguments, CodeBuffer cb)
     * throws BSFException;
     * public void compileExpr (String source, int lineNo, int columnNo,
     * Object expr, CodeBuffer cb) throws BSFException;
     * public void compileScript (String source, int lineNo, int columnNo,
     * Object script, CodeBuffer cb) throws BSFException;
     */
    @Override
    public void declareBean(final BSFDeclaredBean bean) throws BSFException {
        try {
            this.interpreter.set(bean.name, bean.bean);
        } catch (final EvalError e) {
            throw new BSFException("error declaring bean: " + bean.name + " : "
                    + e.toString());
        }
    }

    /** {@inheritDoc} */
    @Override
    public void undeclareBean(final BSFDeclaredBean bean) throws BSFException {
        try {
            this.interpreter.unset(bean.name);
        } catch (final EvalError e) {
            throw new BSFException("bsh internal error: " + e.toString());
        }
    }

    /** {@inheritDoc} */
    @Override
    public void terminate() {}

    /**
     * Source info.
     *
     * @param source
     *            the source
     * @param lineNo
     *            the line no
     * @param columnNo
     *            the column no
     * @return the string
     */
    private String sourceInfo(final String source, final int lineNo,
            final int columnNo) {
        return " BSF info: " + source + " at line: " + lineNo
                + " column: columnNo";
    }
}
