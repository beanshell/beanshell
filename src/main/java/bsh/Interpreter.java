/*****************************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one                *
 * or more contributor license agreements.  See the NOTICE file              *
 * distributed with this work for additional information                     *
 * regarding copyright ownership.  The ASF licenses this file                *
 * to you under the Apache License, Version 2.0 (the                         *
 * "License"); you may not use this file except in compliance                *
 * with the License.  You may obtain a copy of the License at                *
 *                                                                           *
 *     http://www.apache.org/licenses/LICENSE-2.0                            *
 *                                                                           *
 * Unless required by applicable law or agreed to in writing,                *
 * software distributed under the License is distributed on an               *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY                    *
 * KIND, either express or implied.  See the License for the                 *
 * specific language governing permissions and limitations                   *
 * under the License.                                                        *
 *                                                                           *
 *                                                                           *
 * This file is part of the BeanShell Java Scripting distribution.           *
 * Documentation and updates may be found at http://www.beanshell.org/       *
 * Patrick Niemeyer (pat@pat.net)                                            *
 * Author of Learning Java, O'Reilly & Associates                            *
 *                                                                           *
 *****************************************************************************/


package bsh;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.PrintStream;
import java.io.Reader;
import java.io.Serializable;
import java.io.StringReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
    The BeanShell script interpreter.

    An instance of Interpreter can be used to source scripts and evaluate
    statements or expressions.
    <p>
    Here are some examples:

    <p><blockquote><pre>
        Interpeter bsh = new Interpreter();

        // Evaluate statements and expressions
        bsh.eval("foo=Math.sin(0.5)");
        bsh.eval("bar=foo*5; bar=Math.cos(bar);");
        bsh.eval("for(i=0; i<10; i++) { print(\"hello\"); }");
        // same as above using java syntax and apis only
        bsh.eval("for(int i=0; i<10; i++) { System.out.println(\"hello\"); }");

        // Source from files or streams
        bsh.source("myscript.bsh");  // or bsh.eval("source(\"myscript.bsh\")");

        // Use set() and get() to pass objects in and out of variables
        bsh.set( "date", new Date() );
        Date date = (Date)bsh.get( "date" );
        // This would also work:
        Date date = (Date)bsh.eval( "date" );

        bsh.eval("year = date.getYear()");
        Integer year = (Integer)bsh.get("year");  // primitives use wrappers

        // With Java1.3+ scripts can implement arbitrary interfaces...
        // Script an awt event handler (or source it from a file, more likely)
        bsh.eval( "actionPerformed( e ) { print( e ); }");
        // Get a reference to the script object (implementing the interface)
        ActionListener scriptedHandler =
            (ActionListener)bsh.eval("return (ActionListener)this");
        // Use the scripted event handler normally...
        new JButton.addActionListener( script );
    </pre></blockquote>
    <p>

    In the above examples we showed a single interpreter instance, however
    you may wish to use many instances, depending on the application and how
    you structure your scripts.  Interpreter instances are very light weight
    to create, however if you are going to execute the same script repeatedly
    and require maximum performance you should consider scripting the code as
    a method and invoking the scripted method each time on the same interpreter
    instance (using eval()).
    <p>

    See the BeanShell User's Manual for more information.
*/
public class Interpreter
    implements Runnable, ConsoleInterface, Serializable, AutoCloseable
{
    /* --- Begin static members --- */

    private static final long serialVersionUID = 1L;
    public static final String VERSION = "2.1b0";
    /*
        Debug utils are static so that they are reachable by code that doesn't
        necessarily have an interpreter reference (e.g. tracing in utils).
        In the future we may want to allow debug/trace to be turned on on
        a per interpreter basis, in which case we'll need to use the parent
        reference in some way to determine the scope of the command that
        turns it on or off.
    */
    public static final ThreadLocal<Boolean> DEBUG = ThreadLocal.withInitial(()->Boolean.FALSE);
    public static boolean TRACE;
    public static boolean COMPATIBIILTY;

    // This should be per instance
    transient static PrintStream debug;
    static String systemLineSeparator = "\n"; // default

    static {
        staticInit();
    }

    /** Shared system object visible under bsh.system */
    private static final This SYSTEM_OBJECT = This.getThis(new NameSpace(null, null, "bsh.system"), null);

    /** Shared system object visible under bsh.system */
    public static void setShutdownOnExit(final boolean value) {
        try {
            SYSTEM_OBJECT.getNameSpace().setVariable("shutdownOnExit", Boolean.valueOf(value), false);
        } catch (final UtilEvalError utilEvalError) {
            throw new IllegalStateException(utilEvalError);
        }
    }

    /**
        Strict Java mode
        @see #setStrictJava( boolean )
    */
    private boolean strictJava = false;

    /* --- End static members --- */

    /* --- Instance data --- */

    transient Parser parser;
    NameSpace globalNameSpace;
    transient Reader in;
    transient PrintStream out;
    transient PrintStream err;
    ConsoleInterface console;

    /** If this interpeter is a child of another, the parent */
    Interpreter parent;

    /** The name of the file or other source that this interpreter is reading */
    String sourceFileInfo;

    /** by default in interactive mode System.exit() on EOF */
    private boolean exitOnEOF = true;

    protected boolean
        evalOnly,       // Interpreter has no input stream, use eval() only
        interactive;    // Interpreter has a user, print prompts, etc.

    /** Control the verbose printing of results for the show() command. */
    private boolean showResults = true;

    /**
     * Compatibility mode. When {@code true} missing classes are tried to create from corresponding java source files.
     * Default value is {@code false}, could be changed to {@code true} by setting the system property
     * "bsh.compatibility" to "true".
     */
    private boolean compatibility = COMPATIBIILTY;

    /* --- End instance data --- */

    /**
        The main constructor.
        All constructors should now pass through here.

        @param namespace If namespace is non-null then this interpreter's
        root namespace will be set to the one provided.  If it is null a new
        one will be created for it.
        @param parent The parent interpreter if this interpreter is a child
            of another.  May be null.  Children share a BshClassManager with
            their parent instance.
        @param sourceFileInfo An informative string holding the filename
        or other description of the source from which this interpreter is
        reading... used for debugging.  May be null.
    */
    public Interpreter(
        Reader in, PrintStream out, PrintStream err,
        boolean interactive, NameSpace namespace,
        Interpreter parent, String sourceFileInfo )
    {
        parser = new Parser( in );
        long t1 = 0;
        if (Interpreter.DEBUG.get())
            t1=System.currentTimeMillis();
        this.in = in;
        this.out = out;
        this.err = err;
        this.interactive = interactive;
        debug = err;
        this.parent = parent;
        if ( parent != null )
            setStrictJava( parent.getStrictJava() );

        this.sourceFileInfo = sourceFileInfo;

        BshClassManager bcm = BshClassManager.createClassManager( this );

        if ( namespace == null ) {
            globalNameSpace = new NameSpace(namespace, bcm, "global");
            initRootSystemObject();
        } else try {
            globalNameSpace = namespace;
            if ( ! (globalNameSpace.getVariable("bsh") instanceof This) )
                initRootSystemObject();
        } catch (final UtilEvalError e) {
            throw new IllegalStateException(e);
        }

        if ( interactive )
            loadRCFiles();

        if ( Interpreter.DEBUG.get() )
            Interpreter.debug("Time to initialize interpreter: ",
                    (System.currentTimeMillis() - t1));
    }

    public Interpreter(
        Reader in, PrintStream out, PrintStream err,
        boolean interactive, NameSpace namespace)
    {
        this( in, out, err, interactive, namespace, null, null );
    }

    public Interpreter(
        Reader in, PrintStream out, PrintStream err, boolean interactive)
    {
        this(in, out, err, interactive, null);
    }

    /**
        Construct a new interactive interpreter attached to the specified
        console using the specified parent namespace.
    */
    public Interpreter(ConsoleInterface console, NameSpace globalNameSpace) {

        this( console.getIn(), console.getOut(), console.getErr(),
            true, globalNameSpace );

        setConsole( console );
    }

    /**
        Construct a new interactive interpreter attached to the specified
        console.
    */
    public Interpreter(ConsoleInterface console) {
        this(console, null);
    }

    /**
        Create an interpreter for evaluation only.
    */
    public Interpreter()
    {
        this( new StringReader(""),
            System.out, System.err, false, null );
        evalOnly = true;
        setu( "bsh.evalOnly", Primitive.TRUE );
    }

    // End constructors

    /**
        Attach a console
        Note: this method is incomplete.
    */
    public void setConsole( ConsoleInterface console ) {
        this.console = console;
        setu( "bsh.console", console );
        // redundant with constructor
        setOut( console.getOut() );
        setErr( console.getErr() );
        // need to set the input stream - reinit the parser?
    }

    private void initRootSystemObject()
    {
        BshClassManager bcm = getClassManager();
        // bsh
        setu("bsh", new NameSpace(null, bcm, "Bsh Object" ).getThis( this ) );

        // bsh.system
        setu( "bsh.system", SYSTEM_OBJECT);
        setu( "bsh.shared", SYSTEM_OBJECT); // alias

        // bsh.help
        This helpText = new NameSpace(null, bcm, "Bsh Command Help Text" ).getThis( this );
        setu( "bsh.help", helpText );

        // bsh.cwd
        setu( "bsh.cwd", System.getProperty("user.dir") );

        // bsh.interactive
        setu( "bsh.interactive", interactive ? Primitive.TRUE : Primitive.FALSE );

        // bsh.evalOnly
        setu( "bsh.evalOnly", Primitive.FALSE );
    }

    /**
        Set the global namespace for this interpreter.
        <p>

        Note: This is here for completeness.  If you're using this a lot
        it may be an indication that you are doing more work than you have
        to.  For example, caching the interpreter instance rather than the
        namespace should not add a significant overhead.  No state other
        than the debug status is stored in the interpreter.
        <p>

        All features of the namespace can also be accessed using the
        interpreter via eval() and the script variable 'this.namespace'
        (or global.namespace as necessary).
    */
    public void setNameSpace( NameSpace globalNameSpace ) {
        this.globalNameSpace = globalNameSpace;
    }

    /**
        Get the global namespace of this interpreter.
        <p>

        Note: This is here for completeness.  If you're using this a lot
        it may be an indication that you are doing more work than you have
        to.  For example, caching the interpreter instance rather than the
        namespace should not add a significant overhead.  No state other than
        the debug status is stored in the interpreter.
        <p>

        All features of the namespace can also be accessed using the
        interpreter via eval() and the script variable 'this.namespace'
        (or global.namespace as necessary).
    */
    public NameSpace getNameSpace() {
        return globalNameSpace;
    }

    /**
        Run the text only interpreter on the command line or specify a file.
    */
    public static void main( String [] args )
    {
        if ( args.length > 0 ) {
            String filename = args[0];

            String [] bshArgs;
            if ( args.length > 1 ) {
                bshArgs = new String [ args.length -1 ];
                System.arraycopy( args, 1, bshArgs, 0, args.length-1 );
            } else
                bshArgs = new String [0];

            try (Interpreter interpreter = new Interpreter()) {
                interpreter.setu( "bsh.args", bshArgs );
                Object result =
                    interpreter.source( filename, interpreter.globalNameSpace );
                if ( result instanceof Class )
                    try {
                        invokeMain( (Class<?>)result, bshArgs );
                    } catch ( Exception e )
                    {
                        Object o = e;
                        if ( e instanceof InvocationTargetException )
                            o = ((InvocationTargetException)e)
                                .getTargetException();
                        System.err.println(
                            "Class: "+result+" main method threw exception:"+o);
                    }
            } catch ( FileNotFoundException e ) {
                System.err.println("File not found: "+e);
            } catch ( TargetError e ) {
                System.err.println("Script threw exception: "+e);
                if ( e.inNativeCode() )
                    e.printStackTrace( DEBUG.get(), System.err );
            } catch ( EvalError e ) {
                System.err.println("Evaluation Error: "+e);
            } catch ( IOException e ) {
                System.err.println("I/O Error: "+e);
            }
        } else
        {

            try (Interpreter interpreter =
                new Interpreter( new CommandLineReader(
                        new InputStreamReader(System.in)),
                        System.out, System.err, true )) {
                interpreter.run();
            } catch (IOException e) {
                System.err.println("I/O Error: "+e);
            }
        }
    }

    public static void invokeMain( Class<?> clas, String [] args )
        throws Exception
    {
        Method main = Reflect.resolveJavaMethod(
            null/*BshClassManager*/, clas, "main",
            new Class [] { String [].class }, true/*onlyStatic*/ );
        if ( main != null )
            main.invoke( null, new Object [] { args } );
    }

    /**
        Run interactively.  (printing prompts, etc.)
    */
    public void run()
    {
        if(evalOnly)
            throw new RuntimeException("bsh Interpreter: No stream");

        /*
          We'll print our banner using eval(String) in order to
          exercise the parser and get the basic expression classes loaded...
          This ameliorates the delay after typing the first statement.
        */
        if ( interactive && null == getParent() ) try {
            eval("printBanner();");
        } catch ( EvalError e ) {
            println(
                "BeanShell "+VERSION+" - by Pat Niemeyer (pat@pat.net)");
        }

        // init the callstack.
        CallStack callstack = new CallStack( globalNameSpace );

        SimpleNode node = null;
        boolean eof = false;
        int idx = -1;
        while( !Thread.interrupted() && !eof )
        {
            try
            {
                if ( interactive )
                    prompt(getBshPrompt());

                eof = isEOF();

                if( get_jjtree().nodeArity() > 0 )  // number of child nodes
                {

                    node = (SimpleNode)(get_jjtree().rootNode());

                    if(DEBUG.get())
                        node.dump(">");

                    Object ret = node.eval( callstack, this );

                    // sanity check during development
                    if ( callstack.depth() > 1 )
                        throw new InterpreterError(
                            "Callstack growing: "+callstack);

                    if(ret instanceof ReturnControl)
                        ret = ((ReturnControl)ret).value;

                    if( ret != Primitive.VOID )
                    {
                        setu("$_", ret);
                        if (interactive)
                            setu("$"+(++idx%10), ret);
                        if ( showResults )
                            println("--> $" + (idx%10) + " = " + StringUtil.typeValueString(ret));
                    } else if ( showResults )
                        println("--> void");
                }
            }
            catch(ParseException e)
            {
                if ( !interactive || !e.getMessage().contains("<EOF>") )
                    error("Parser Error: " + e.getMessage(DEBUG.get()));
                if ( DEBUG.get() )
                    e.printStackTrace();
                if( !interactive )
                    eof = true;
                parser.reInitInput(in);
            }
            catch(InterpreterError e)
            {
                error("Internal Error: " + e.getMessage());
                if(!interactive)
                    eof = true;
            }
            catch(TargetError e)
            {
                error("Target Exception: " + e.getMessage() );
                if ( e.inNativeCode() )
                    e.printStackTrace( DEBUG.get(), err );
                if(!interactive)
                    eof = true;
                setu("$_e", e.getTarget());
            }
            catch (EvalError e)
            {
                if ( interactive )
                    error( "Evaluation Error: "+e.getMessage() );
                else
                    error( "Evaluation Error: "+e.getRawMessage() );
                if(DEBUG.get())
                    e.printStackTrace();
                if(!interactive)
                    eof = true;
            }
            catch(Exception e)
            {
                error("Unknown error: " + e);
                if ( DEBUG.get() )
                    e.printStackTrace();
                if(!interactive)
                    eof = true;
            }
            finally
            {
                get_jjtree().reset();
                // reinit the callstack
                if ( callstack.depth() > 1 ) {
                    callstack.clear();
                    callstack.push( globalNameSpace );
                }
            }
        }

        if ( interactive && exitOnEOF )
            System.exit(0);
    }

    // begin source and eval

    /**
        Read text from fileName and eval it.
    */
    public Object source( String filename, NameSpace nameSpace )
        throws FileNotFoundException, IOException, EvalError
    {
        File file = pathToFile( filename );
        Interpreter.debug("Sourcing file: ", file);
        Reader sourceIn = new BufferedReader( new FileReader(file) );
        try {
            return eval( sourceIn, nameSpace, filename );
        } finally {
            sourceIn.close();
        }
    }

    /**
        Read text from fileName and eval it.
        Convenience method.  Use the global namespace.
    */
    public Object source( String filename )
        throws FileNotFoundException, IOException, EvalError
    {
        return source( filename, globalNameSpace );
    }

    /**
        Spawn a non-interactive local interpreter to evaluate text in the
        specified namespace.

        Return value is the evaluated object (or corresponding primitive
        wrapper).

        @param sourceFileInfo is for information purposes only.  It is used to
        display error messages (and in the future may be made available to
        the script).
        @throws EvalError on script problems
        @throws TargetError on unhandled exceptions from the script
    */
    /*
        Note: we need a form of eval that passes the callstack through...
    */
    /*
    Can't this be combined with run() ?
    run seems to have stuff in it for interactive vs. non-interactive...
    compare them side by side and see what they do differently, aside from the
    exception handling.
    */

    public Object eval(
        Reader in, NameSpace nameSpace, String sourceFileInfo
            /*, CallStack callstack */ )
        throws EvalError
    {
        Object retVal = null;
        Interpreter.debug("eval: nameSpace = ", nameSpace);

        /*
            Create non-interactive local interpreter for this namespace
            with source from the input stream and out/err same as
            this interpreter.
        */
        try (Interpreter localInterpreter = new Interpreter(
                in, out, err, false, nameSpace, this, sourceFileInfo  )) {

            CallStack callstack = new CallStack( nameSpace );

            SimpleNode node = null;
            boolean eof = false;
            while( !Thread.interrupted() && !eof )
            {
                try
                {
                    eof = localInterpreter.isEOF();
                    if (localInterpreter.get_jjtree().nodeArity() > 0)
                    {
                        if( node != null )
                            node.lastToken.next = null;  // prevent OutOfMemoryError

                        node = (SimpleNode)localInterpreter.get_jjtree().rootNode();
                        // nodes remember from where they were sourced
                        node.setSourceFile( sourceFileInfo );

                        if ( TRACE )
                            println( "// " +node.getText() );

                        retVal = node.eval( callstack, localInterpreter );

                        // sanity check during development
                        if ( callstack.depth() > 1 )
                            throw new InterpreterError(
                                "Callstack growing: "+callstack);

                        if ( retVal instanceof ReturnControl ) {
                            retVal = ((ReturnControl)retVal).value;
                            break; // non-interactive, return control now
                        }
                    }
                } catch(ParseException e) {
                    if ( DEBUG.get() )
                        // show extra "expecting..." info
                        error( e.getMessage(DEBUG.get()) );

                    // add the source file info and throw again
                    e.setErrorSourceFile( sourceFileInfo );
                    throw e;
                } catch ( InterpreterError e ) {
                    throw new EvalError(
                        "Sourced file: "+sourceFileInfo+" internal Error: "
                        + e.getMessage(), node, callstack, e);
                } catch ( TargetError e ) {
                    // failsafe, set the Line as the origin of the error.
                    if ( e.getNode()==null )
                        e.setNode( node );
                    e.reThrow("Sourced file: "+sourceFileInfo);
                } catch ( EvalError e) {
                    if ( DEBUG.get())
                        e.printStackTrace();
                    // failsafe, set the Line as the origin of the error.
                    if ( e.getNode()==null )
                        e.setNode( node );
                    e.reThrow( "Sourced file: "+sourceFileInfo );
                } catch ( Exception e) {
                    if ( DEBUG.get())
                        e.printStackTrace();
                    throw new EvalError(
                        "Sourced file: "+sourceFileInfo+" unknown error: "
                        + e.getMessage(), node, callstack, e);
                } finally {
                    localInterpreter.get_jjtree().reset();

                    // reinit the callstack
                    if ( callstack.depth() > 1 ) {
                        callstack.clear();
                        callstack.push( nameSpace );
                    }
                }
            }
            // release shared resources before auto closing.
            localInterpreter.out = null;
            localInterpreter.err = null;
        } catch (IOException ioe) {
            throw new EvalError("Sourced file: "+sourceFileInfo+" "
                    + ioe.toString(), null, null, ioe);
        }
        return Primitive.unwrap( retVal );
    }

    /**
        Evaluate the inputstream in this interpreter's global namespace.
    */
    public Object eval( Reader in ) throws EvalError
    {
        return eval( in, globalNameSpace, "eval stream" );
    }

    /**
        Evaluate the string in this interpreter's global namespace.
    */
    public Object eval( String statements ) throws EvalError {
        Interpreter.debug("eval(String): ", statements);
        return eval(statements, globalNameSpace);
    }

    /**
        Evaluate the string in the specified namespace.
    */
    public Object eval( String statements, NameSpace nameSpace )
        throws EvalError
    {
        String s = ( statements.endsWith(";") ? statements : statements+";" );
        return eval(
            new StringReader(s), nameSpace,
            "inline evaluation of: ``"+ showEvalString(s)+"''" );
    }

    private String showEvalString( String s ) {
        s = s.replace('\n', ' ');
        s = s.replace('\r', ' ');
        if ( s.length() > 80 )
            s = s.substring( 0, 80 ) + " . . . ";
        return s;
    }

    // end source and eval

    /**
        Print an error message in a standard format on the output stream
        associated with this interpreter. On the GUI console this will appear
        in red, etc.
    */
    public final void error( Object o ) {
        if ( console != null )
                console.error( "// Error: " + o +"\n" );
        else {
            err.println("// Error: " + o );
            err.flush();
        }
    }

    // ConsoleInterface
    // The interpreter reflexively implements the console interface that it
    // uses.  Should clean this up by using an inner class to implement the
    // console for us.

    /**
        Get the input stream associated with this interpreter.
        This may be be stdin or the GUI console.
    */
    public Reader getIn() { return in; }

    /**
        Get the outptut stream associated with this interpreter.
        This may be be stdout or the GUI console.
    */
    public PrintStream getOut() { return out; }

    /**
        Get the error output stream associated with this interpreter.
        This may be be stderr or the GUI console.
    */
    public PrintStream getErr() { return err; }


    /** Attempt the release of open resources.
     * @throws IOException */
    public void close() throws IOException {
        if ( null != err ) {
            err.close();
            err = null;
        }
        if ( null != out ) {
            out.close();
            out = null;
        }
        if ( null != in ) {
            in.close();
            in = null;
        }
    }

    @Override
    public final void println( Object o )
    {
        print( String.valueOf(o) + systemLineSeparator );
    }

    @Override
    public final void print( Object o )
    {
        if (console != null) {
            console.print(o);
        } else {
            out.print(o);
            out.flush();
        }
    }

    @Override
    public final void prompt(String prompt) {
        if (console != null) {
            console.prompt(prompt);
        } else {
            print(prompt);
        }
    }

    // End ConsoleInterface

    /**
        Print a debug message on debug stream associated with this interpreter
        only if debugging is turned on.
    */
    public final static void debug(Object... msg)
    {
        if ( DEBUG.get() ) {
            StringBuilder sb = new StringBuilder();
            for ( Object m : msg )
                sb.append(m);
            debug.println("// Debug: " + sb.toString());
        }
    }

    /*
        Primary interpreter set and get variable methods
        Note: These are squeltching errors... should they?
    */

    /**
        Get the value of the name.
        name may be any value. e.g. a variable or field
    */
    public Object get( String name ) throws EvalError {
        try {
            Object ret = globalNameSpace.get( name, this );
            return Primitive.unwrap( ret );
        } catch ( UtilEvalError e ) {
            throw e.toEvalError( SimpleNode.JAVACODE, new CallStack() );
        }
    }

    /**
        Unchecked get for internal use
    */
    Object getu( String name ) {
        try {
            return get( name );
        } catch ( EvalError e ) {
            throw new InterpreterError("set: "+e, e);
        }
    }

    /**
        Assign the value to the name.
        name may evaluate to anything assignable. e.g. a variable or field.
    */
    public void set( String name, Object value )
        throws EvalError
    {
        // map null to Primtive.NULL coming in...
        if ( value == null )
            value = Primitive.NULL;

        CallStack callstack = new CallStack();
        try {
            if ( Name.isCompound( name ) )
            {
                LHS lhs = globalNameSpace.getNameResolver( name ).toLHS(
                    callstack, this );
                lhs.assign( value, false );
            } else // optimization for common case
                globalNameSpace.setVariable( name, value, false );
        } catch ( UtilEvalError e ) {
            throw e.toEvalError( SimpleNode.JAVACODE, callstack );
        }
    }

    /**
        Unchecked set for internal use
    */
    void setu(String name, Object value) {
        try {
            set(name, value);
        } catch ( EvalError e ) {
            throw new InterpreterError("set: "+e, e);
        }
    }

    public void set(String name, long value) throws EvalError {
        set(name, new Primitive(value));
    }
    public void set(String name, int value) throws EvalError {
        set(name, new Primitive(value));
    }
    public void set(String name, double value) throws EvalError {
        set(name, new Primitive(value));
    }
    public void set(String name, float value) throws EvalError {
        set(name, new Primitive(value));
    }
    public void set(String name, boolean value) throws EvalError {
        set(name, value ? Primitive.TRUE : Primitive.FALSE);
    }

    /**
        Unassign the variable name.
        Name should evaluate to a variable.
    */
    public void unset( String name )
        throws EvalError
    {
        /*
            We jump through some hoops here to handle arbitrary cases like
            unset("bsh.foo");
        */
        CallStack callstack = new CallStack();
        try {
            LHS lhs = globalNameSpace.getNameResolver( name ).toLHS(
                callstack, this );

            if ( lhs.type != LHS.VARIABLE )
                throw new EvalError("Can't unset, not a variable: "+name,
                    SimpleNode.JAVACODE, new CallStack());

            lhs.nameSpace.unsetVariable( name );
        } catch ( UtilEvalError e ) {
            throw new EvalError( e.getMessage(),
                SimpleNode.JAVACODE, new CallStack(), e);
        }
    }

    // end primary set and get methods

    /**
        Get a reference to the interpreter (global namespace), cast
        to the specified interface type.  Assuming the appropriate
        methods of the interface are defined in the interpreter, then you may
        use this interface from Java, just like any other Java object.
        <p>

        For example:
        <pre>
            Interpreter interpreter = new Interpreter();
            // define a method called run()
            interpreter.eval("run() { ... }");

            // Fetch a reference to the interpreter as a Runnable
            Runnable runnable =
                (Runnable)interpreter.getInterface( Runnable.class );
        </pre>
        <p>

        Note that the interpreter does *not* require that any or all of the
        methods of the interface be defined at the time the interface is
        generated.  However if you attempt to invoke one that is not defined
        you will get a runtime exception.
        <p>

        Note also that this convenience method has exactly the same effect as
        evaluating the script:
        <pre>
            (Type)this;
        </pre>
        <p>

        For example, the following is identical to the previous example:
        <p>

        <pre>
            // Fetch a reference to the interpreter as a Runnable
            Runnable runnable =
                (Runnable)interpreter.eval( "(Runnable)this" );
        </pre>
        <p>

        <em>Version requirement</em> Although standard Java interface types
        are always available, to be used with arbitrary interfaces this
        feature requires that you are using Java 1.3 or greater.
        <p>

        @throws EvalError if the interface cannot be generated because the
        version of Java does not support the proxy mechanism.
    */
    public Object getInterface( Class<?> interf ) throws EvalError
    {
        return globalNameSpace.getThis( this ).getInterface( interf );
    }

    /*  Methods for interacting with Parser */

    private JJTParserState get_jjtree() {
        return parser.jjtree;
    }

    private boolean isEOF() throws ParseException {
        return parser.Line();
    }

    /*  End methods for interacting with Parser */

    void loadRCFiles() {
        try {
            // Default is c:\windows under win98, $HOME under Unix
            String rcfile =
                System.getProperty("user.home") + File.separator + ".bshrc";
            source( rcfile, globalNameSpace );
        } catch ( Exception e ) {
            // squeltch security exception, filenotfoundexception
            Interpreter.debug("Could not find rc file: ", e);
        }
    }

    /**
        Localize a path to the file name based on the bsh.cwd interpreter
        working directory.
    */
    public File pathToFile( String fileName )
        throws IOException
    {
        File file = new File( fileName );

        // if relative, fix up to bsh.cwd
        if ( !file.isAbsolute() ) {
            String cwd = (String)getu("bsh.cwd");
            file = new File( cwd + File.separator + fileName );
        }

        // The canonical file name is also absolute.
        // No need for getAbsolutePath() here...
        return new File( file.getCanonicalPath() );
    }

    public static void redirectOutputToFile( String filename )
    {
        try {
            @SuppressWarnings("resource")
            PrintStream pout = new PrintStream(
                new FileOutputStream( filename ) );
            System.setOut( pout );
            System.setErr( pout );
        } catch ( IOException e ) {
            System.err.println("Can't redirect output to file: "+filename );
        }
    }

    /**
        Set an external class loader to be used as the base classloader
        for BeanShell.  The base classloader is used for all classloading
        unless/until the addClasspath()/setClasspath()/reloadClasses()
        commands are called to modify the interpreter's classpath.  At that
        time the new paths /updated paths are added on top of the base
        classloader.
        <p>

        BeanShell will use this at the same point it would otherwise use the
        plain Class.forName().
        i.e. if no explicit classpath management is done from the script
        (addClassPath(), setClassPath(), reloadClasses()) then BeanShell will
        only use the supplied classloader.  If additional classpath management
        is done then BeanShell will perform that in addition to the supplied
        external classloader.
        However BeanShell is not currently able to reload
        classes supplied through the external classloader.
        <p>

        @see BshClassManager#setClassLoader( ClassLoader )
    */
    public void setClassLoader( ClassLoader externalCL ) {
        getClassManager().setClassLoader( externalCL );
    }

    /**
        Get the class manager associated with this interpreter
        (the BshClassManager of this interpreter's global namespace).
        This is primarily a convenience method.
    */
    public BshClassManager getClassManager()
    {
        return getNameSpace().getClassManager();
    }

    /**
        Set strict Java mode on or off.
        This mode attempts to make BeanShell syntax behave as Java
        syntax, eliminating conveniences like loose variables, etc.
        When enabled, variables are required to be declared or initialized
        before use and method arguments are reqired to have types.
        <p>

        This mode will become more strict in a future release when
        classes are interpreted and there is an alternative to scripting
        objects as method closures.
    */
    public void setStrictJava( boolean b ) {
        this.strictJava = b;
    }

    /**
        @see #setStrictJava( boolean )
    */
    public boolean getStrictJava() {
        return this.strictJava;
    }

    static void staticInit()
    {
        try {
            systemLineSeparator = System.getProperty("line.separator");
            debug = System.err;
            DEBUG.set(Boolean.getBoolean("debug"));
            TRACE = Boolean.getBoolean("trace");
            COMPATIBIILTY = Boolean.getBoolean("bsh.compatibility");
            String outfilename = System.getProperty("outfile");
            if ( outfilename != null )
                redirectOutputToFile( outfilename );
        } catch ( SecurityException e ) {
            System.err.println("Could not init static:"+e);
        } catch ( Exception e ) {
            System.err.println("Could not init static(2):"+e);
        } catch ( Throwable e ) {
            System.err.println("Could not init static(3):"+e);
        }
    }

    /**
        Specify the source of the text from which this interpreter is reading.
        Note: there is a difference between what file the interrpeter is
        sourcing and from what file a method was originally parsed.  One
        file may call a method sourced from another file.  See SimpleNode
        for origination file info.
        @see bsh.SimpleNode#getSourceFile()
    */
    public String getSourceFileInfo() {
        if ( sourceFileInfo != null )
            return sourceFileInfo;
        else
            return "<unknown source>";
    }

    /**
        Get the parent Interpreter of this interpreter, if any.
        Currently this relationship implies the following:
            1) Parent and child share a BshClassManager
            2) Children indicate the parent's source file information in error
            reporting.
        When created as part of a source() / eval() the child also shares
        the parent's namespace.  But that is not necessary in general.
    */
    public Interpreter getParent() {
        return parent;
    }

    public void setOut( PrintStream out ) {
        this.out = out;
    }
    public void setErr( PrintStream err ) {
        this.err = err;
    }

    /**
        De-serialization setup.
        Default out and err streams to stdout, stderr if they are null.
    */
    private void readObject(ObjectInputStream stream)
        throws java.io.IOException, ClassNotFoundException
    {
        stream.defaultReadObject();

        // set transient fields
        if ( console != null ) {
            setOut( console.getOut() );
            setErr( console.getErr() );
        } else {
            setOut( System.out );
            setErr( System.err );
        }
    }

    /**
        Get the prompt string defined by the getBshPrompt() method in the
        global namespace.  This may be from the getBshPrompt() command or may
        be defined by the user as with any other method.
        Defaults to "bsh % " if the method is not defined or there is an error.
    */
    private String prompt = null;  // is this caching correct?
    private String getBshPrompt()
    {
        if (null != prompt)
            return prompt;
        try {
            prompt = (String)eval("getBshPrompt()");
        } catch ( Exception e ) {
            prompt = "bsh % ";
        }
        return prompt;
    }

    /**
        Specify whether, in interactive mode, the interpreter exits Java upon
        end of input.  If true, when in interactive mode the interpreter will
        issue a System.exit(0) upon eof.  If false the interpreter no
        System.exit() will be done.
        <p/>
        Note: if you wish to cause an EOF externally you can try closing the
        input stream.  This is not guaranteed to work in older versions of Java
        due to Java limitations, but should work in newer JDK/JREs.  (That was
        the motivation for the Java NIO package).
    */
    public void setExitOnEOF( boolean value ) {
        exitOnEOF = value;
    }

    /**
        Turn on/off the verbose printing of results as for the show()
         command.
        If this interpreter has a parent the call is delegated.
        See the BeanShell show() command.
    */
    public void setShowResults( boolean showResults ) {
        this.showResults = showResults;
    }
    /**
     Show on/off verbose printing status for the show() command.
     See the BeanShell show() command.
     If this interpreter has a parent the call is delegated.
     */
    public boolean getShowResults()  {
        return showResults;
    }

    /**
     * Compatibility mode. When {@code true} missing classes are tried to create from corresponding java source files.
     * The Default value is {@code false}. This could be changed to {@code true} by setting the system property
     * "bsh.compatibility" to "true".
     *
     * @see #setCompatibility(boolean)
     */
    public boolean getCompatibility() {
        return compatibility;
    }

    /**
     * Setting compatibility mode. When {@code true} missing classes are tried to create from corresponding java source
     * files. The Default value is {@code false}. This could be changed to {@code true} by setting the system property
     * "bsh.compatibility" to "true".
     *
     * @see #getCompatibility()
     */
    public void setCompatibility(final boolean value) {
        compatibility = value;
    }

    public static String getSaveClassesDir() {
        return System.getProperty("bsh.debugClasses");
    }

    public static boolean getSaveClasses()  {
        return getSaveClassesDir() != null && !getSaveClassesDir().isEmpty();
    }
}

