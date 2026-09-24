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
    This is the BeanShell adapter for Apache Bean Scripting Framework 2.x.
        <p>
    It is an implementation of the BSFEngine class, allowing BSF aware
    applications to use BeanShell as a scripting language.
    <p>
    I believe this implementation is complete (with some hesitation about the
    the usefullness of the compileXXX() style methods - provided by the base
    utility class).

    See http://www.beanshell.org/manual/bsf.html for some more info.
    <p/>

    @author Pat Niemeyer
*/
public class BeanShellBSFEngine extends BSFEngineImpl
{
    Interpreter interpreter;
    boolean installedApplyMethod;

    public void initialize ( BSFManager mgr, String lang, Vector declaredBeans)
    throws BSFException
    {
        super.initialize( mgr, lang, declaredBeans );

        interpreter = new Interpreter();

        // declare the bsf manager for callbacks, etc.
        try {
            interpreter.set( "bsf", mgr );
        } catch ( EvalError e ) {
            BSFException bsf = new BSFException(BSFException.REASON_OTHER_ERROR, "bsh internal error: "+e, e);
            bsf.initCause(e);
            throw bsf;
        }

        for(int i=0; i<declaredBeans.size(); i++)
        {
            BSFDeclaredBean bean = (BSFDeclaredBean)declaredBeans.get(i);
            declareBean( bean );
        }
    }

    public void setDebug (boolean debug)
    {
        interpreter.DEBUG.set(debug);
    }

    /**
        Invoke method name on the specified bsh scripted object.
        The object may be null to indicate the global namespace of the
        interpreter.
        @param object may be null for the global namespace.
    */
    public Object call( Object object, String name, Object[] args )
        throws BSFException
    {
        /*
            If object is null use the interpreter's global scope.
        */
        if ( object == null )
            try {
                object = interpreter.get("global");
            } catch ( EvalError e ) {
                BSFException bsf = new BSFException(BSFException.REASON_OTHER_ERROR, "bsh internal error: "+e, e);
                bsf.initCause(e);
                throw bsf;
            }

        if ( object instanceof bsh.This )
            try
            {
                Object value = ((bsh.This)object).invokeMethod( name, args );
                return Primitive.unwrap( value );
            } catch ( InterpreterError e )
            {
                BSFException bsf = new BSFException(BSFException.REASON_UNKNOWN_LANGUAGE,
                    "BeanShell interpreter internal error: "+e, e);
                bsf.initCause(e);
                throw bsf;

            } catch ( TargetError e2 )
            {
                BSFException bsf = new BSFException(BSFException.REASON_EXECUTION_ERROR,
                    "The application script threw an exception: "
                    + e2.getTarget(), e2 );
                bsf.initCause(e2);
                throw bsf;

            } catch ( EvalError e3 )
            {
                BSFException bsf = new BSFException(BSFException.REASON_OTHER_ERROR, "BeanShell script error: "+e3, e3);
                bsf.initCause(e3);
                throw bsf;
            }
        else
            throw new BSFException(
                "Cannot invoke method: "+name
                +". Object: "+object +" is not a BeanShell scripted object.");
    }


    /**
        A helper BeanShell method that implements the anonymous method apply
        proposed by BSF.  Note that the script below could use the standard
        bsh eval() method to set the variables and apply the text, however
        then I'd have to escape quotes, etc.
    */
    final static String bsfApplyMethod =
        "_bsfApply( _bsfNames, _bsfArgs, _bsfText ) {"
            +"for(i=0;i<_bsfNames.length;i++)"
                +"this.namespace.setVariable(_bsfNames[i], _bsfArgs[i],false);"
            +"return this.interpreter.eval(_bsfText, this.namespace);"
        +"}";

    /**
        This is an implementation of the BSF apply() method.
        It exectutes the funcBody text in an "anonymous" method call with
        arguments.
    */
    /*
        Note: the apply() method may be supported directly in BeanShell in an
        upcoming release and would not require special support here.
    */
    public Object apply (
        String source, int lineNo, int columnNo, Object funcBody,
        Vector namesVec, Vector argsVec )
       throws BSFException
    {
        if ( namesVec.size() != argsVec.size() )
            throw new BSFException("number of params/names mismatch");
        if ( !(funcBody instanceof String) )
            throw new BSFException("apply: functino body must be a string");

        String [] names = new String [ namesVec.size() ];
        namesVec.copyInto(names);
        Object [] args = new Object [ argsVec.size() ];
        argsVec.copyInto(args);

        try
        {
            if ( !installedApplyMethod )
            {
                interpreter.eval( bsfApplyMethod );
                installedApplyMethod = true;
            }

            bsh.This global = (bsh.This)interpreter.get("global");
            Object value = global.invokeMethod(
                "_bsfApply", new Object [] { names, args, (String)funcBody } );
            return Primitive.unwrap( value );

        } catch ( InterpreterError e )
        {
            BSFException bsf = new BSFException(BSFException.REASON_UNKNOWN_LANGUAGE,
                "BeanShell interpreter internal error: "+e
                + errorMessage(source,lineNo,columnNo, e), e);

            bsf.initCause(e);
            throw bsf;

        } catch ( TargetError e2 )
        {
            BSFException bsf =  new BSFException(BSFException.REASON_EXECUTION_ERROR,
                "The application script threw an exception: "
                + e2.getTarget()
                + errorMessage(source,lineNo,columnNo, e2), e2);
            bsf.initCause(e2);
            throw bsf;

        } catch ( EvalError e3 )
        {
            BSFException bsf = new BSFException(BSFException.REASON_OTHER_ERROR,
                "BeanShell script error: "+e3
                + errorMessage(source,lineNo,columnNo, e3), e3);
            bsf.initCause(e3);
            throw bsf;
        }
    }

    public Object eval (
        String source, int lineNo, int columnNo, Object expr)
        throws BSFException
    {
        if ( ! (expr instanceof String) )
            throw new BSFException("BeanShell expression must be a string");

        try {
            return interpreter.eval( ((String)expr) );
        } catch ( InterpreterError e )
        {

            BSFException bsf = new BSFException(BSFException.REASON_UNKNOWN_LANGUAGE,
                "BeanShell interpreter internal error: "+e
                + errorMessage(source,lineNo,columnNo, e), e);
            bsf.initCause(e);
            throw bsf;

        } catch ( TargetError e2 )
        {
            BSFException bsf = new BSFException(BSFException.REASON_EXECUTION_ERROR,
                "The application script threw an exception: "
                + e2.getTarget()
                + errorMessage(source,lineNo,columnNo, e2), e2);
            bsf.initCause(e2);
            throw bsf;

        } catch ( EvalError e3 )
        {
            BSFException bsf = new BSFException(BSFException.REASON_OTHER_ERROR,
                "BeanShell script error: "+e3
                + errorMessage(source,lineNo,columnNo, e3), e3);
            bsf.initCause(e3);
            throw bsf;
        }
    }


    public void exec (String source, int lineNo, int columnNo, Object script)
        throws BSFException
    {
        eval( source, lineNo, columnNo, script );
    }


/*
    I don't quite understand these compile methods.  The default impl
    will use the CodeBuffer utility to produce an example (Test) class that
    turns around and invokes the BSF Manager to call the script again.

    I assume a statically compiled language would return a real implementation
    class adapter here?  But in source code form?  Would't it be more likely
    to generate bytecode?

    And shouldn't a non-compiled language simply return a standard
    precompiled adapter to itself?  The indirection of building a source
    class to call the scripting engine (possibly through the interpreter)
    seems kind of silly.
*/
/*
    public void compileApply (String source, int lineNo, int columnNo,
        Object funcBody, Vector paramNames, Vector arguments, CodeBuffer cb)
        throws BSFException;

    public void compileExpr (String source, int lineNo, int columnNo,
        Object expr, CodeBuffer cb) throws BSFException;

    public void compileScript (String source, int   lineNo, int columnNo,
        Object script, CodeBuffer cb) throws BSFException;
*/

    public void declareBean (BSFDeclaredBean bean)
        throws BSFException
    {
        try {
            interpreter.set( bean.name, bean.bean);
        } catch ( EvalError e ) {

            BSFException bsf = new BSFException(BSFException.REASON_OTHER_ERROR, "error declaring bean: "+bean.name
                    +" : "+e, e);
            bsf.initCause(e);
            throw bsf;

        }
    }

    public void undeclareBean (BSFDeclaredBean bean)
        throws BSFException
    {
        try {
            interpreter.unset( bean.name );
        } catch ( EvalError e ) {
            BSFException bsf = new BSFException(BSFException.REASON_OTHER_ERROR, "bsh internal error: "+e, e);
            bsf.initCause(e);
            throw bsf;
        }
    }

    public void terminate () { }

    /**
     * Build an error message
     *
     * @param source
     * @param lineNo
     * @param columnNo
     * @param error
     * @return
     */
    private String errorMessage(String source, int lineNo, int columnNo, EvalError error)
    {
        StringBuilder msg = new StringBuilder();
        msg.append(" BSF info: ");
        msg.append(source);
        msg.append(" at line: ");

        if (lineNo > 0) {

            msg.append(lineNo);
            msg.append(" column: ");
            msg.append(columnNo);

        } else {
            msg.append( error.getErrorLineNumber());
        }
        msg.append(" Error: ");
        msg.append(error.getErrorText());
        return msg.toString();
    }

    /**
     *
     * @param source
     * @param lineNo
     * @param columnNo
     * @param error
     * @return
     */
    private String errorMessage(String source, int lineNo, int columnNo, InterpreterError error)
    {
        StringBuilder msg = new StringBuilder();
        msg.append(" BSF info: ");
        msg.append(source);

        if (lineNo > 0) {

            msg.append(" at line: ");
            msg.append(lineNo);
            msg.append(" column: ");
            msg.append(columnNo);

        }
        msg.append(" Error: ");
        if (error.getCause() != null)
            msg.append(error.getCause().getMessage());
        else
            msg.append(error.getMessage());

        return msg.toString();
    }
}
