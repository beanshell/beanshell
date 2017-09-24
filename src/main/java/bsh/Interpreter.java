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
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.PrintStream;
import java.io.Reader;
import java.io.Serializable;
import java.io.StringReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * The BeanShell script interpreter.
 *
 * An instance of Interpreter can be used to source scripts and evaluate
 * statements or expressions.
 * <p>
 * Here are some examples:
 *
 * <p>
 * <blockquote>
 *
 * <pre>
 * Interpeter bsh = new Interpreter();
 * // Evaluate statements and expressions
 * bsh.eval("foo=Math.sin(0.5)");
 * bsh.eval("bar=foo*5; bar=Math.cos(bar);");
 * bsh.eval("for(i=0; i<10; i++) { print(\"hello\"); }");
 * // same as above using java syntax and apis only
 * bsh.eval("for(int i=0; i<10; i++) { System.out.println(\"hello\"); }");
 * // Source from files or streams
 * bsh.source("myscript.bsh"); // or bsh.eval("source(\"myscript.bsh\")");
 * // Use set() and get() to pass objects in and out of variables
 * bsh.set("date", new Date());
 * Date date = (Date) bsh.get("date");
 * // This would also work:
 * Date date = (Date) bsh.eval("date");
 * bsh.eval("year = date.getYear()");
 * Integer year = (Integer) bsh.get("year"); // primitives use wrappers
 * // With Java1.3+ scripts can implement arbitrary interfaces...
 * // Script an awt event handler (or source it from a file, more likely)
 * bsh.eval("actionPerformed(e) { print(e); }");
 * // Get a reference to the script object (implementing the interface)
 * ActionListener scriptedHandler = (ActionListener) bsh
 *         .eval("return (ActionListener)this");
 * // Use the scripted event handler normally...
 * new JButton.addActionListener(script);
 * </pre>
 *
 * </blockquote>
 * <p>
 *
 * In the above examples we showed a single interpreter instance, however
 * you may wish to use many instances, depending on the application and how
 * you structure your scripts. Interpreter instances are very light weight
 * to create, however if you are going to execute the same script repeatedly
 * and require maximum performance you should consider scripting the code as
 * a method and invoking the scripted method each time on the same interpreter
 * instance (using eval()).
 * <p>
 *
 * See the BeanShell User's Manual for more information.
 */
public class Interpreter implements Runnable, ConsoleInterface, Serializable {
    /* --- Begin static members --- */

    /** The Constant serialVersionUID. */
    private static final long serialVersionUID = 1L;

    /** The Constant VERSION. */
    public static final String VERSION = "2.0b6";

    /**
     * Debug utils are static so that they are reachable by code that doesn't
     * necessarily have an interpreter reference (e.g. tracing in utils).
     * In the future we may want to allow debug/trace to be turned on on
     * a per interpreter basis, in which case we'll need to use the parent
     * reference in some way to determine the scope of the command that
     * turns it on or off.
     */
    public static boolean DEBUG, TRACE, LOCALSCOPING;

    /** The debug, This should be per instance. */
    static transient PrintStream debug;

    /** The system line separator. */
    static String systemLineSeparator = "\n"; // default
    static {
        staticInit();
    }
    /** Shared system object visible under bsh.system. */
    static This sharedObject;

    /**
     * Strict Java mode.
     *
     * @see #setStrictJava(boolean)
     */
    private boolean strictJava = false;

    /** The parser. */
    transient Parser parser;

    /** The global name space. */
    NameSpace globalNameSpace;

    /** The in. */
    transient Reader in;

    /** The out. */
    transient PrintStream out;

    /** The err. */
    transient PrintStream err;

    /** The console. */
    ConsoleInterface console;

    /** If this interpeter is a child of another, the parent. */
    Interpreter parent;

    /**
     * The name of the file or other source that this interpreter is reading.
     */
    String sourceFileInfo;
    /** by default in interactive mode System.exit() on EOF. */
    private boolean exitOnEOF = true;

    /** The interactive. */
    protected boolean evalOnly, // Interpreter has no input stream, use eval()
                                // only
            interactive; // Interpreter has a user, print prompts, etc.
    /** Control the verbose printing of results for the show() command. */
    private boolean showResults;

    /**
     * The main constructor.
     * All constructors should now pass through here.
     * Instantiates a new interpreter.
     *
     * @param in
     *            the in
     * @param out
     *            the out
     * @param err
     *            the err
     * @param interactive
     *            the interactive
     * @param namespace
     *            If namespace is non-null then this interpreter's root namespace
     *            will be set to the one provided. If it is null a new one will
     *            be created for it.
     * @param parent
     *            The parent interpreter if this interpreter is a child
     *            of another. May be null. Children share a BshClassManager with
     *            their parent instance.
     * @param sourceFileInfo
     *            An informative string holding the filename or other description
     *            of the source from which this interpreter is reading...
     *            used for debugging. May be null.
     */
    public Interpreter(final Reader in, final PrintStream out,
            final PrintStream err, final boolean interactive,
            final NameSpace namespace, final Interpreter parent,
            final String sourceFileInfo) {
        // System.out.println("New Interpreter: "+this +", sourcefile =
        // "+sourceFileInfo);
        this.parser = new Parser(in);
        final long t1 = System.currentTimeMillis();
        this.in = in;
        this.out = out;
        this.err = err;
        this.interactive = interactive;
        debug = err;
        this.parent = parent;
        if (parent != null)
            this.setStrictJava(parent.getStrictJava());
        this.sourceFileInfo = sourceFileInfo;
        final BshClassManager bcm = BshClassManager.createClassManager(this);
        if (namespace == null)
            this.globalNameSpace = new NameSpace(null, bcm, "global");
        else
            this.globalNameSpace = namespace;
        // now done in NameSpace automatically when root
        // The classes which are imported by default
        // globalNameSpace.loadDefaultImports();
        /*
         * Create the root "bsh" system object if it doesn't exist.
         */
        if (!(this.getu("bsh") instanceof bsh.This))
            this.initRootSystemObject();
        if (interactive)
            this.loadRCFiles();
        final long t2 = System.currentTimeMillis();
        if (Interpreter.DEBUG)
            Interpreter.debug("Time to initialize interpreter: " + (t2 - t1));
    }

    /**
     * Instantiates a new interpreter.
     *
     * @param in
     *            the in
     * @param out
     *            the out
     * @param err
     *            the err
     * @param interactive
     *            the interactive
     * @param namespace
     *            the namespace
     */
    public Interpreter(final Reader in, final PrintStream out,
            final PrintStream err, final boolean interactive,
            final NameSpace namespace) {
        this(in, out, err, interactive, namespace, null, null);
    }

    /**
     * Instantiates a new interpreter.
     *
     * @param in
     *            the in
     * @param out
     *            the out
     * @param err
     *            the err
     * @param interactive
     *            the interactive
     */
    public Interpreter(final Reader in, final PrintStream out,
            final PrintStream err, final boolean interactive) {
        this(in, out, err, interactive, null);
    }

    /**
     * Construct a new interactive interpreter attached to the specified
     * console using the specified parent namespace.
     *
     * @param console
     *            the console
     * @param globalNameSpace
     *            the global name space
     */
    public Interpreter(final ConsoleInterface console,
            final NameSpace globalNameSpace) {
        this(console.getIn(), console.getOut(), console.getErr(), true,
                globalNameSpace);
        this.setConsole(console);
    }

    /**
     * Construct a new interactive interpreter attached to the specified
     * console.
     *
     * @param console
     *            the console
     */
    public Interpreter(final ConsoleInterface console) {
        this(console, null);
    }

    /**
     * Create an interpreter for evaluation only.
     */
    public Interpreter() {
        this(new StringReader(""), System.out, System.err, false, null);
        this.evalOnly = true;
        this.setu("bsh.evalOnly", Primitive.TRUE);
    }

    /**
     * Attach a console
     * Note: this method is incomplete.
     *
     * @param console
     *            the new console
     */
    public void setConsole(final ConsoleInterface console) {
        this.console = console;
        this.setu("bsh.console", console);
        // redundant with constructor
        this.setOut(console.getOut());
        this.setErr(console.getErr());
        // need to set the input stream - reinit the parser?
    }

    /**
     * Inits the root system object.
     */
    private void initRootSystemObject() {
        final BshClassManager bcm = this.getClassManager();
        // bsh
        this.setu("bsh", new NameSpace(null, bcm, "Bsh Object").getThis(this));
        // init the static shared sharedObject if it's not there yet
        if (sharedObject == null)
            sharedObject = new NameSpace(null, bcm, "Bsh Shared System Object")
                    .getThis(this);
        // bsh.system
        this.setu("bsh.system", sharedObject);
        this.setu("bsh.shared", sharedObject); // alias
        // bsh.help
        final This helpText = new NameSpace(null, bcm, "Bsh Command Help Text")
                .getThis(this);
        this.setu("bsh.help", helpText);
        // bsh.cwd
        try {
            this.setu("bsh.cwd", System.getProperty("user.dir"));
        } catch (final SecurityException e) {
            // applets can't see sys props
            this.setu("bsh.cwd", ".");
        }
        // bsh.interactive
        this.setu("bsh.interactive",
                this.interactive ? Primitive.TRUE : Primitive.FALSE);
        // bsh.evalOnly
        this.setu("bsh.evalOnly",
                this.evalOnly ? Primitive.TRUE : Primitive.FALSE);
    }

    /**
     * Set the global namespace for this interpreter.
     * <p>
     *
     * Note: This is here for completeness. If you're using this a lot
     * it may be an indication that you are doing more work than you have
     * to. For example, caching the interpreter instance rather than the
     * namespace should not add a significant overhead. No state other
     * than the debug status is stored in the interpreter.
     * <p>
     *
     * All features of the namespace can also be accessed using the
     * interpreter via eval() and the script variable 'this.namespace'
     * (or global.namespace as necessary).
     *
     * @param globalNameSpace
     *            the new name space
     */
    public void setNameSpace(final NameSpace globalNameSpace) {
        this.globalNameSpace = globalNameSpace;
    }

    /**
     * Get the global namespace of this interpreter.
     * <p>
     *
     * Note: This is here for completeness. If you're using this a lot
     * it may be an indication that you are doing more work than you have
     * to. For example, caching the interpreter instance rather than the
     * namespace should not add a significant overhead. No state other than
     * the debug status is stored in the interpreter.
     * <p>
     *
     * All features of the namespace can also be accessed using the
     * interpreter via eval() and the script variable 'this.namespace'
     * (or global.namespace as necessary).
     *
     * @return the name space
     */
    public NameSpace getNameSpace() {
        return this.globalNameSpace;
    }

    /**
     * Run the text only interpreter on the command line or specify a file.
     *
     * @param args
     *            the arguments
     */
    public static void main(final String[] args) {
        if (args.length > 0) {
            final String filename = args[0];
            String[] bshArgs;
            if (args.length > 1) {
                bshArgs = new String[args.length - 1];
                System.arraycopy(args, 1, bshArgs, 0, args.length - 1);
            } else
                bshArgs = new String[0];
            final Interpreter interpreter = new Interpreter();
            // System.out.println("run i = "+interpreter);
            interpreter.setu("bsh.args", bshArgs);
            try {
                final Object result = interpreter.source(filename,
                        interpreter.globalNameSpace);
                if (result instanceof Class)
                    try {
                        invokeMain((Class) result, bshArgs);
                    } catch (final Exception e) {
                        Object o = e;
                        if (e instanceof InvocationTargetException)
                            o = ((InvocationTargetException) e)
                                    .getTargetException();
                        System.err.println("Class: " + result
                                + " main method threw exception:" + o);
                    }
            } catch (final FileNotFoundException e) {
                System.out.println("File not found: " + e);
            } catch (final TargetError e) {
                System.out.println("Script threw exception: " + e);
                if (e.inNativeCode())
                    e.printStackTrace(DEBUG, System.err);
            } catch (final EvalError e) {
                System.out.println("Evaluation Error: " + e);
            } catch (final IOException e) {
                System.out.println("I/O Error: " + e);
            }
        } else {
            // Workaround for JDK bug 4071281, where system.in.available()
            // returns too large a value. This bug has been fixed in JDK 1.2.
            InputStream src;
            if (System.getProperty("os.name").startsWith("Windows")
                    && System.getProperty("java.version").startsWith("1.1."))
                src = new FilterInputStream(System.in) {

                    @Override
                    public int available() throws IOException {
                        return 0;
                    }
                };
            else
                src = System.in;
            final Reader in = new CommandLineReader(new InputStreamReader(src));
            final Interpreter interpreter = new Interpreter(in, System.out,
                    System.err, true);
            interpreter.run();
        }
    }

    /**
     * Invoke main.
     *
     * @param clas
     *            the clas
     * @param args
     *            the args
     * @throws Exception
     *             the exception
     */
    public static void invokeMain(final Class clas, final String[] args)
            throws Exception {
        final Method main = Reflect.resolveJavaMethod(null/* BshClassManager */,
                clas, "main", new Class[] {String[].class},
                true/* onlyStatic */);
        if (main != null)
            main.invoke(null, new Object[] {args});
    }

    /**
     * Run interactively. (printing prompts, etc.)
     */
    public void run() {
        if (this.evalOnly)
            throw new RuntimeException("bsh Interpreter: No stream");
        /*
         * We'll print our banner using eval(String) in order to
         * exercise the parser and get the basic expression classes loaded...
         * This ameliorates the delay after typing the first statement.
         */
        if (this.interactive)
            try {
                this.eval("printBanner();");
            } catch (final EvalError e) {
                this.println("BeanShell " + VERSION
                        + " - by Pat Niemeyer (pat@pat.net)");
            }
        // init the callstack.
        final CallStack callstack = new CallStack(this.globalNameSpace);
        boolean eof = false;
        while (!eof)
            try {
                // try to sync up the console
                System.out.flush();
                System.err.flush();
                Thread.yield(); // this helps a little
                if (this.interactive)
                    this.print(this.getBshPrompt());
                eof = this.Line();
                if (this.get_jjtree().nodeArity() > 0) { // number of child nodes
                    final SimpleNode node = (SimpleNode) this.get_jjtree()
                            .rootNode();
                    if (DEBUG)
                        node.dump(">");
                    Object ret = node.eval(callstack, this);
                    // sanity check during development
                    if (callstack.depth() > 1)
                        throw new InterpreterError(
                                "Callstack growing: " + callstack);
                    if (ret instanceof ReturnControl)
                        ret = ((ReturnControl) ret).value;
                    if (ret != Primitive.VOID) {
                        this.setu("$_", ret);
                        if (this.showResults)
                            this.println("<" + ret + ">");
                    }
                }
            } catch (final ParseException e) {
                this.error("Parser Error: " + e.getMessage(DEBUG));
                if (DEBUG)
                    e.printStackTrace();
                if (!this.interactive)
                    eof = true;
                this.parser.reInitInput(this.in);
            } catch (final InterpreterError e) {
                this.error("Internal Error: " + e.getMessage());
                e.printStackTrace();
                if (!this.interactive)
                    eof = true;
            } catch (final TargetError e) {
                this.error("// Uncaught Exception: " + e);
                if (e.inNativeCode())
                    e.printStackTrace(DEBUG, this.err);
                if (!this.interactive)
                    eof = true;
                this.setu("$_e", e.getTarget());
            } catch (final EvalError e) {
                if (this.interactive)
                    this.error("EvalError: " + e.toString());
                else
                    this.error("EvalError: " + e.getMessage());
                if (DEBUG)
                    e.printStackTrace();
                if (!this.interactive)
                    eof = true;
            } catch (final Exception e) {
                this.error("Unknown error: " + e);
                if (DEBUG)
                    e.printStackTrace();
                if (!this.interactive)
                    eof = true;
            } catch (final TokenMgrError e) {
                this.error("Error parsing input: " + e);
                /*
                 * We get stuck in infinite loops here when unicode escapes
                 * fail. Must re-init the char stream reader
                 * (ASCII_UCodeESC_CharStream.java)
                 */
                this.parser.reInitTokenInput(this.in);
                if (!this.interactive)
                    eof = true;
            } finally {
                this.get_jjtree().reset();
                // reinit the callstack
                if (callstack.depth() > 1) {
                    callstack.clear();
                    callstack.push(this.globalNameSpace);
                }
            }
        if (this.interactive && this.exitOnEOF)
            System.exit(0);
    }

    /**
     * Read text from fileName and eval it.
     *
     * @param filename
     *            the filename
     * @param nameSpace
     *            the name space
     * @return the object
     * @throws FileNotFoundException
     *             the file not found exception
     * @throws IOException
     *             Signals that an I/O exception has occurred.
     * @throws EvalError
     *             the eval error
     */
    public Object source(final String filename, final NameSpace nameSpace)
            throws FileNotFoundException, IOException, EvalError {
        final File file = this.pathToFile(filename);
        if (Interpreter.DEBUG)
            debug("Sourcing file: " + file);
        final Reader sourceIn = new BufferedReader(new FileReader(file));
        try {
            return this.eval(sourceIn, nameSpace, filename);
        } finally {
            sourceIn.close();
        }
    }

    /**
     * Read text from fileName and eval it.
     * Convenience method. Use the global namespace.
     *
     * @param filename
     *            the filename
     * @return the object
     * @throws FileNotFoundException
     *             the file not found exception
     * @throws IOException
     *             Signals that an I/O exception has occurred.
     * @throws EvalError
     *             the eval error
     */
    public Object source(final String filename)
            throws FileNotFoundException, IOException, EvalError {
        return this.source(filename, this.globalNameSpace);
    }

    /**
     * Spawn a non-interactive local interpreter to evaluate text in the
     * specified namespace.
     *
     * Return value is the evaluated object (or corresponding primitive
     * wrapper).
     *
     * @param in
     *            the in
     * @param nameSpace
     *            the name space
     * @param sourceFileInfo
     *            is for information purposes only. It is used to
     *            display error messages (and in the future may be made
     *            available to
     *            the script).
     * @return the object
     * @throws EvalError
     *             on script problems
     */
    public Object eval(final Reader in, final NameSpace nameSpace,
            final String sourceFileInfo
    /* , CallStack callstack */) throws EvalError {
        Object retVal = null;
        if (Interpreter.DEBUG)
            debug("eval: nameSpace = " + nameSpace);
        /*
         * Create non-interactive local interpreter for this namespace
         * with source from the input stream and out/err same as
         * this interpreter.
         */
        final Interpreter localInterpreter = new Interpreter(in, this.out,
                this.err, false, nameSpace, this, sourceFileInfo);
        final CallStack callstack = new CallStack(nameSpace);
        boolean eof = false;
        while (!eof) {
            SimpleNode node = null;
            try {
                eof = localInterpreter.Line();
                if (localInterpreter.get_jjtree().nodeArity() > 0) {
                    node = (SimpleNode) localInterpreter.get_jjtree()
                            .rootNode();
                    // quick filter for when we're running as a compiler only
                    if (getSaveClasses()
                            && !(node instanceof BSHClassDeclaration)
                            && !(node instanceof BSHImportDeclaration)
                            && !(node instanceof BSHPackageDeclaration))
                        continue;
                    // nodes remember from where they were sourced
                    node.setSourceFile(sourceFileInfo);
                    if (TRACE)
                        this.println("// " + node.getText());
                    retVal = node.eval(callstack, localInterpreter);
                    // sanity check during development
                    if (callstack.depth() > 1)
                        throw new InterpreterError(
                                "Callstack growing: " + callstack);
                    if (retVal instanceof ReturnControl) {
                        retVal = ((ReturnControl) retVal).value;
                        break; // non-interactive, return control now
                    }
                    if (localInterpreter.showResults
                            && retVal != Primitive.VOID)
                        this.println("<" + retVal + ">");
                }
            } catch (final ParseException e) {
                /*
                 * throw new EvalError(
                 * "Sourced file: "+sourceFileInfo+" parser Error: "
                 * + e.getMessage(DEBUG), node, callstack);
                 */
                if (DEBUG)
                    // show extra "expecting..." info
                    this.error(e.getMessage(DEBUG));
                // add the source file info and throw again
                e.setErrorSourceFile(sourceFileInfo);
                throw e;
            } catch (final InterpreterError e) {
                final EvalError evalError = new EvalError("Sourced file: "
                        + sourceFileInfo + " internal Error: " + e.getMessage(),
                        node, callstack);
                evalError.initCause(e);
                throw evalError;
            } catch (final TargetError e) {
                // failsafe, set the Line as the origin of the error.
                if (e.getNode() == null)
                    e.setNode(node);
                e.reThrow("Sourced file: " + sourceFileInfo);
            } catch (final EvalError e) {
                if (DEBUG)
                    e.printStackTrace();
                // failsafe, set the Line as the origin of the error.
                if (e.getNode() == null)
                    e.setNode(node);
                e.reThrow("Sourced file: " + sourceFileInfo);
            } catch (final Exception e) {
                final EvalError evalError = new EvalError("Sourced file: "
                        + sourceFileInfo + " unknown error: " + e.getMessage(),
                        node, callstack);
                evalError.initCause(e);
                throw evalError;
            } catch (final TokenMgrError e) {
                final EvalError evalError = new EvalError(
                        "Sourced file: " + sourceFileInfo
                                + " Token Parsing Error: " + e.getMessage(),
                        node, callstack);
                evalError.initCause(e);
                throw evalError;
            } finally {
                localInterpreter.get_jjtree().reset();
                // reinit the callstack
                if (callstack.depth() > 1) {
                    callstack.clear();
                    callstack.push(nameSpace);
                }
            }
        }
        return Primitive.unwrap(retVal);
    }

    /**
     * Evaluate the inputstream in this interpreter's global namespace.
     *
     * @param in
     *            the in
     * @return the object
     * @throws EvalError
     *             the eval error
     */
    public Object eval(final Reader in) throws EvalError {
        return this.eval(in, this.globalNameSpace, "eval stream");
    }

    /**
     * Evaluate the string in this interpreter's global namespace.
     *
     * @param statements
     *            the statements
     * @return the object
     * @throws EvalError
     *             the eval error
     */
    public Object eval(final String statements) throws EvalError {
        if (Interpreter.DEBUG)
            debug("eval(String): " + statements);
        return this.eval(statements, this.globalNameSpace);
    }

    /**
     * Evaluate the string in the specified namespace.
     *
     * @param statements
     *            the statements
     * @param nameSpace
     *            the name space
     * @return the object
     * @throws EvalError
     *             the eval error
     */
    public Object eval(final String statements, final NameSpace nameSpace)
            throws EvalError {
        final String s = statements.endsWith(";") ? statements
                : statements + ";";
        return this.eval(new StringReader(s), nameSpace,
                "inline evaluation of: ``" + this.showEvalString(s) + "''");
    }

    /**
     * Show eval string.
     *
     * @param s
     *            the s
     * @return the string
     */
    private String showEvalString(String s) {
        s = s.replace('\n', ' ');
        s = s.replace('\r', ' ');
        if (s.length() > 80)
            s = s.substring(0, 80) + " . . . ";
        return s;
    }

    /**
     * Print an error message in a standard format on the output stream
     * associated with this interpreter. On the GUI console this will appear
     * in red, etc.
     *
     * @param o
     *            the o
     */
    public final void error(final Object o) {
        if (this.console != null)
            this.console.error("// Error: " + o + "\n");
        else {
            this.err.println("// Error: " + o);
            this.err.flush();
        }
    }

    /**
     * Get the input stream associated with this interpreter.
     * This may be be stdin or the GUI console.
     *
     * ConsoleInterface
     * The interpreter reflexively implements the console interface that it
     * uses. Should clean this up by using an inner class to implement the
     * console for us.
     *
     * @return the in
     */
    public Reader getIn() {
        return this.in;
    }

    /**
     * Get the outptut stream associated with this interpreter.
     * This may be be stdout or the GUI console.
     *
     * @return the out
     */
    public PrintStream getOut() {
        return this.out;
    }

    /**
     * Get the error output stream associated with this interpreter.
     * This may be be stderr or the GUI console.
     *
     * @return the err
     */
    public PrintStream getErr() {
        return this.err;
    }

    /** {@inheritDoc} */
    public final void println(final Object o) {
        this.print(String.valueOf(o) + systemLineSeparator);
    }

    /** {@inheritDoc} */
    public final void print(final Object o) {
        if (this.console != null)
            this.console.print(o);
        else {
            this.out.print(o);
            this.out.flush();
        }
    }

    /**
     * Print a debug message on debug stream associated with this interpreter
     * only if debugging is turned on.
     *
     * @param s
     *            the s
     */
    public static final void debug(final String s) {
        if (DEBUG)
            debug.println("// Debug: " + s);
    }

    /**
     * Gets the.
     *
     * Primary interpreter set and get variable methods
     * Note: These are squeltching errors... should they?
     **
     * Get the value of the name.
     * name may be any value. e.g. a variable or field
     * @param name
     *            the name
     * @return the object
     * @throws EvalError
     *             the eval error
     */
    public Object get(final String name) throws EvalError {
        try {
            final Object ret = this.globalNameSpace.get(name, this);
            return Primitive.unwrap(ret);
        } catch (final UtilEvalError e) {
            throw e.toEvalError(SimpleNode.JAVACODE, new CallStack());
        }
    }

    /**
     * Unchecked get for internal use.
     *
     * @param name
     *            the name
     * @return the u
     */
    Object getu(final String name) {
        try {
            return this.get(name);
        } catch (final EvalError e) {
            throw new InterpreterError("set: " + e);
        }
    }

    /**
     * Assign the value to the name.
     * name may evaluate to anything assignable. e.g. a variable or field.
     *
     * @param name
     *            the name
     * @param value
     *            the value
     * @throws EvalError
     *             the eval error
     */
    public void set(final String name, Object value) throws EvalError {
        // map null to Primtive.NULL coming in...
        if (value == null)
            value = Primitive.NULL;
        final CallStack callstack = new CallStack();
        try {
            if (Name.isCompound(name)) {
                final LHS lhs = this.globalNameSpace.getNameResolver(name)
                        .toLHS(callstack, this);
                lhs.assign(value, false);
            } else // optimization for common case
                this.globalNameSpace.setVariable(name, value, false);
        } catch (final UtilEvalError e) {
            throw e.toEvalError(SimpleNode.JAVACODE, callstack);
        }
    }

    /**
     * Unchecked set for internal use.
     *
     * @param name
     *            the name
     * @param value
     *            the value
     */
    void setu(final String name, final Object value) {
        try {
            this.set(name, value);
        } catch (final EvalError e) {
            throw new InterpreterError("set: " + e);
        }
    }

    /**
     * Sets the.
     *
     * @param name
     *            the name
     * @param value
     *            the value
     * @throws EvalError
     *             the eval error
     */
    public void set(final String name, final long value) throws EvalError {
        this.set(name, new Primitive(value));
    }

    /**
     * Sets the.
     *
     * @param name
     *            the name
     * @param value
     *            the value
     * @throws EvalError
     *             the eval error
     */
    public void set(final String name, final int value) throws EvalError {
        this.set(name, new Primitive(value));
    }

    /**
     * Sets the.
     *
     * @param name
     *            the name
     * @param value
     *            the value
     * @throws EvalError
     *             the eval error
     */
    public void set(final String name, final double value) throws EvalError {
        this.set(name, new Primitive(value));
    }

    /**
     * Sets the.
     *
     * @param name
     *            the name
     * @param value
     *            the value
     * @throws EvalError
     *             the eval error
     */
    public void set(final String name, final float value) throws EvalError {
        this.set(name, new Primitive(value));
    }

    /**
     * Sets the.
     *
     * @param name
     *            the name
     * @param value
     *            the value
     * @throws EvalError
     *             the eval error
     */
    public void set(final String name, final boolean value) throws EvalError {
        this.set(name, value ? Primitive.TRUE : Primitive.FALSE);
    }

    /**
     * Unassign the variable name.
     * Name should evaluate to a variable.
     *
     * @param name
     *            the name
     * @throws EvalError
     *             the eval error
     */
    public void unset(final String name) throws EvalError {
        /*
         * We jump through some hoops here to handle arbitrary cases like
         * unset("bsh.foo");
         */
        final CallStack callstack = new CallStack();
        try {
            final LHS lhs = this.globalNameSpace.getNameResolver(name)
                    .toLHS(callstack, this);
            if (lhs.type != LHS.VARIABLE)
                throw new EvalError("Can't unset, not a variable: " + name,
                        SimpleNode.JAVACODE, new CallStack());
            // lhs.assign(null, false);
            lhs.nameSpace.unsetVariable(name);
        } catch (final UtilEvalError e) {
            throw new EvalError(e.getMessage(), SimpleNode.JAVACODE,
                    new CallStack());
        }
    }

    /**
     * Get a reference to the interpreter (global namespace), cast
     * to the specified interface type. Assuming the appropriate
     * methods of the interface are defined in the interpreter, then you may
     * use this interface from Java, just like any other Java object.
     * <p>
     *
     * For example:
     *
     * <pre>
     * Interpreter interpreter = new Interpreter();
     * // define a method called run()
     * interpreter.eval("run() { ... }");
     * // Fetch a reference to the interpreter as a Runnable
     * Runnable runnable = (Runnable) interpreter.getInterface(Runnable.class);
     * </pre>
     * <p>
     *
     * Note that the interpreter does *not* require that any or all of the
     * methods of the interface be defined at the time the interface is
     * generated. However if you attempt to invoke one that is not defined
     * you will get a runtime exception.
     * <p>
     *
     * Note also that this convenience method has exactly the same effect as
     * evaluating the script:
     *
     * <pre>
     *          (Type)this;
     * </pre>
     * <p>
     *
     * For example, the following is identical to the previous example:
     * <p>
     *
     * <pre>
     *
     * // Fetch a reference to the interpreter as a Runnable
     * Runnable runnable = (Runnable) interpreter.eval("(Runnable)this");
     * </pre>
     * <p>
     *
     * <em>Version requirement</em> Although standard Java interface types
     * are always available, to be used with arbitrary interfaces this
     * feature requires that you are using Java 1.3 or greater.
     * <p>
     *
     * @param interf
     *            the interf
     * @return the interface
     * @throws EvalError
     *             if the interface cannot be generated because the
     *             version of Java does not support the proxy mechanism.
     */
    public Object getInterface(final Class interf) throws EvalError {
        try {
            return this.globalNameSpace.getThis(this).getInterface(interf);
        } catch (final UtilEvalError e) {
            throw e.toEvalError(SimpleNode.JAVACODE, new CallStack());
        }
    }

    /**
     * Gets the jjtree.
     * Methods for interacting with Parser.
     *
     * @return the jjtree
     */
    private JJTParserState get_jjtree() {
        return this.parser.jjtree;
    }

    /**
     * Line.
     *
     * @return true, if successful
     * @throws ParseException
     *             the parse exception
     */
    private boolean Line() throws ParseException {
        return this.parser.Line();
    }

    /**
     * Load RC files.
     *
     * End methods for interacting with Parser
     */
    void loadRCFiles() {
        try {
            final String rcfile =
                    // Default is c:\windows under win98, $HOME under Unix
                    System.getProperty("user.home") + File.separator + ".bshrc";
            this.source(rcfile, this.globalNameSpace);
        } catch (final Exception e) {
            // squeltch security exception, filenotfoundexception
            if (Interpreter.DEBUG)
                debug("Could not find rc file: " + e);
        }
    }

    /**
     * Localize a path to the file name based on the bsh.cwd interpreter
     * working directory.
     *
     * @param fileName
     *            the file name
     * @return the file
     * @throws IOException
     *             Signals that an I/O exception has occurred.
     */
    public File pathToFile(final String fileName) throws IOException {
        File file = new File(fileName);
        // if relative, fix up to bsh.cwd
        if (!file.isAbsolute()) {
            final String cwd = (String) this.getu("bsh.cwd");
            file = new File(cwd + File.separator + fileName);
        }
        // The canonical file name is also absolute.
        // No need for getAbsolutePath() here...
        return new File(file.getCanonicalPath());
    }

    /**
     * Redirect output to file.
     *
     * @param filename
     *            the filename
     */
    public static void redirectOutputToFile(final String filename) {
        try {
            final PrintStream pout = new PrintStream(
                    new FileOutputStream(filename));
            System.setOut(pout);
            System.setErr(pout);
        } catch (final IOException e) {
            System.err.println("Can't redirect output to file: " + filename);
        }
    }

    /**
     * Set an external class loader to be used as the base classloader
     * for BeanShell. The base classloader is used for all classloading
     * unless/until the addClasspath()/setClasspath()/reloadClasses()
     * commands are called to modify the interpreter's classpath. At that
     * time the new paths /updated paths are added on top of the base
     * classloader.
     * <p>
     *
     * BeanShell will use this at the same point it would otherwise use the
     * plain Class.forName().
     * i.e. if no explicit classpath management is done from the script
     * (addClassPath(), setClassPath(), reloadClasses()) then BeanShell will
     * only use the supplied classloader. If additional classpath management
     * is done then BeanShell will perform that in addition to the supplied
     * external classloader.
     * However BeanShell is not currently able to reload
     * classes supplied through the external classloader.
     * <p>
     *
     * @param externalCL
     *            the new class loader
     * @see BshClassManager#setClassLoader(ClassLoader)
     */
    public void setClassLoader(final ClassLoader externalCL) {
        this.getClassManager().setClassLoader(externalCL);
    }

    /**
     * Get the class manager associated with this interpreter
     * (the BshClassManager of this interpreter's global namespace).
     * This is primarily a convenience method.
     *
     * @return the class manager
     */
    public BshClassManager getClassManager() {
        return this.getNameSpace().getClassManager();
    }

    /**
     * Set strict Java mode on or off.
     * This mode attempts to make BeanShell syntax behave as Java
     * syntax, eliminating conveniences like loose variables, etc.
     * When enabled, variables are required to be declared or initialized
     * before use and method arguments are reqired to have types.
     * <p>
     *
     * This mode will become more strict in a future release when
     * classes are interpreted and there is an alternative to scripting
     * objects as method closures.
     *
     * @param b
     *            the new strict java
     */
    public void setStrictJava(final boolean b) {
        this.strictJava = b;
    }

    /**
     * Gets the strict java.
     *
     * @return the strict java
     * @see #setStrictJava(boolean)
     */
    public boolean getStrictJava() {
        return this.strictJava;
    }

    /**
     * Static init.
     */
    static void staticInit() {
        /*
         * Apparently in some environments you can't catch the security
         * exception
         * at all... e.g. as an applet in IE ... will probably have to work
         * around
         */
        try {
            systemLineSeparator = System.getProperty("line.separator");
            debug = System.err;
            DEBUG = Boolean.getBoolean("debug");
            TRACE = Boolean.getBoolean("trace");
            LOCALSCOPING = Boolean.getBoolean("localscoping");
            final String outfilename = System.getProperty("outfile");
            if (outfilename != null)
                redirectOutputToFile(outfilename);
        } catch (final SecurityException e) {
            System.err.println("Could not init static:" + e);
        } catch (final Exception e) {
            System.err.println("Could not init static(2):" + e);
        } catch (final Throwable e) {
            System.err.println("Could not init static(3):" + e);
        }
    }

    /**
     * Specify the source of the text from which this interpreter is reading.
     * Note: there is a difference between what file the interrpeter is
     * sourcing and from what file a method was originally parsed. One
     * file may call a method sourced from another file. See SimpleNode
     * for origination file info.
     *
     * @return the source file info
     * @see bsh.SimpleNode#getSourceFile()
     */
    public String getSourceFileInfo() {
        if (this.sourceFileInfo != null)
            return this.sourceFileInfo;
        else
            return "<unknown source>";
    }

    /**
     * Get the parent Interpreter of this interpreter, if any.
     * Currently this relationship implies the following:
     * 1) Parent and child share a BshClassManager
     * 2) Children indicate the parent's source file information in error
     * reporting.
     * When created as part of a source() / eval() the child also shares
     * the parent's namespace. But that is not necessary in general.
     *
     * @return the parent
     */
    public Interpreter getParent() {
        return this.parent;
    }

    /**
     * Sets the out.
     *
     * @param out
     *            the new out
     */
    public void setOut(final PrintStream out) {
        this.out = out;
    }

    /**
     * Sets the err.
     *
     * @param err
     *            the new err
     */
    public void setErr(final PrintStream err) {
        this.err = err;
    }

    /**
     * De-serialization setup.
     * Default out and err streams to stdout, stderr if they are null.
     *
     * @param stream
     *            the stream
     * @throws IOException
     *             Signals that an I/O exception has occurred.
     * @throws ClassNotFoundException
     *             the class not found exception
     */
    private void readObject(final ObjectInputStream stream)
            throws java.io.IOException, ClassNotFoundException {
        stream.defaultReadObject();
        // set transient fields
        if (this.console != null) {
            this.setOut(this.console.getOut());
            this.setErr(this.console.getErr());
        } else {
            this.setOut(System.out);
            this.setErr(System.err);
        }
    }

    /**
     * Get the prompt string defined by the getBshPrompt() method in the
     * global namespace. This may be from the getBshPrompt() command or may
     * be defined by the user as with any other method.
     * Defaults to "bsh % " if the method is not defined or there is an error.
     *
     * @return the bsh prompt
     */
    private String getBshPrompt() {
        try {
            return (String) this.eval("getBshPrompt()");
        } catch (final Exception e) {
            return "bsh % ";
        }
    }

    /**
     * Specify whether, in interactive mode, the interpreter exits Java upon
     * end of input. If true, when in interactive mode the interpreter will
     * issue a System.exit(0) upon eof. If false the interpreter no
     * System.exit() will be done.
     * <p/>
     * Note: if you wish to cause an EOF externally you can try closing the
     * input stream. This is not guaranteed to work in older versions of Java
     * due to Java limitations, but should work in newer JDK/JREs. (That was
     * the motivation for the Java NIO package).
     *
     * @param value
     *            the new exit on EOF
     */
    public void setExitOnEOF(final boolean value) {
        this.exitOnEOF = value; // ug
    }

    /**
     * Turn on/off the verbose printing of results as for the show()
     * command.
     * If this interpreter has a parent the call is delegated.
     * See the BeanShell show() command.
     *
     * @param showResults
     *            the new show results
     */
    public void setShowResults(final boolean showResults) {
        this.showResults = showResults;
    }

    /**
     * Show on/off verbose printing status for the show() command.
     * See the BeanShell show() command.
     * If this interpreter has a parent the call is delegated.
     *
     * @return the show results
     */
    public boolean getShowResults() {
        return this.showResults;
    }

    /**
     * Gets the save classes dir.
     *
     * @return the save classes dir
     */
    public static String getSaveClassesDir() {
        return System.getProperty("saveClasses");
    }

    /**
     * Gets the save classes.
     *
     * @return the save classes
     */
    public static boolean getSaveClasses() {
        return getSaveClassesDir() != null;
    }
}
