package bsh;

/**
    RuntimeEvalError indicates that we cannot continue evaluating the script
    or the script has thrown an exception, but the code was executed where you can't throw an {@link EvalError}.
    @see EvalError
*/
public class RuntimeEvalError extends RuntimeException {

    private EvalError error;

    RuntimeEvalError( String s, Node node, CallStack callstack, Throwable cause ) {
        this.error = new EvalError(s, node, callstack);
    }

    RuntimeEvalError( String s, Node node, CallStack callstack ) {
        this.error = new EvalError(s, node, callstack);
    }

    RuntimeEvalError( EvalError error ) {
        this.error = error;
    }

    /**
        Print the error with line number and stack trace.
    */
    public String getMessage() { return this.error.getMessage(); }

    /**
        Return the error to re-throw, prepending the specified message.
        Method does not throw itself as this messes with the tooling.
    */
    public EvalError reThrow( String msg ) { return this.error.reThrow(msg); }

    /**
        The error has trace info associated with it.
        i.e. It has an AST node that can print its location and source text.
    */
    Node getNode() { return this.error.getNode(); }

    void setNode( Node node ) { this.error.setNode(node); }

    public String getErrorText() { return this.error.getErrorText(); }

    public int getErrorLineNumber() { return this.error.getErrorLineNumber(); }

    public String getErrorSourceFile() { return this.error.getErrorSourceFile(); }

    public String getScriptStackTrace() { return this.error.getScriptStackTrace(); }

    public String getRawMessage() { return this.error.getRawMessage(); }

}

