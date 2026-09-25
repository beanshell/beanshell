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

import java.util.ArrayList;
import java.util.List;

/**
    EvalError indicates that we cannot continue evaluating the node
    and an unrecoverable internal error has corrupted the interpreter
    or callstack.

    This does not include script syntax errors or referring to an undefined variable,
    which are handled by {@link EvalException}.

    @see EvalException
    @see TargetError
*/
public class EvalError extends Exception
{
    private Node node;

    // Note: no way to mutate the Throwable message, must maintain our own
    private String message;

    private final CallStack callstack;

    public EvalError( String s, Node node, CallStack callstack, Throwable cause ) {
        this(s,node,callstack);
        initCause(cause);
    }

    public EvalError( String s, Node node, CallStack callstack ) {
        this.message = s;
        this.node = node;
        // freeze the callstack for the stack trace.
        this.callstack = callstack==null ? null : callstack.copy();
    }

    /**
        Print the error with line number and stack trace.
    */
    public String getMessage()
    {
        return getRawMessage() + getLocationTrace();
    }

    /**
        Render just this error's own source location and script call-stack
        trace (no raw message, no cause chain). Package-private: reused by
        TargetError to render a preserved original trace when a
        catch/finally block throws a new exception wrapping this one.
    */
    String getLocationTrace()
    {
        String trace = getOwnLocation();

        if ( callstack != null ) {
            String stackTrace = getScriptStackTrace();
            if ( !stackTrace.isEmpty() )
                trace = trace +"\n" + stackTrace;
        }

        return trace;
    }

    /**
        Just this error's own source location (no call-stack frames).
        Package-private: used by TargetError to separate the "originally
        thrown at" headline from its (separately elidable) call-chain
        frames -- see TargetError.printTargetError.
    */
    String getOwnLocation()
    {
        if ( node != null )
            return " at line "+ node.getLineNumber()
                + " in "+ node.getSourceFile() + ", near `"+node.getText()+"`";
        // Users should not normally see this.
        return " at an unknown location";
    }

    /**
        Return the error to re-throw, prepending the specified message.
        Method does not throw itself as this messes with the tooling.
    */
    public EvalError reThrow( String msg ) {
        prependMessage( msg );
        return this;
    }

    /**
        The error has trace info associated with it.
        i.e. It has an AST node that can print its location and source text.
    */
    Node getNode() {
        return node;
    }

    void setNode( Node node ) {
        this.node = node;
    }

    /** The frozen call stack captured when this error was constructed.
     * Package-private: used by BSHTryStatement to build a cause-free
     * location-only snapshot (see EvalError(String,Node,CallStack)) when
     * preserving a caught exception's original trace.
     * @return the frozen call stack, or null */
    CallStack getCallStack() {
        return callstack;
    }

    public String getErrorText() {
        if ( node != null )
            return node.getText() ;
        else
            return "<unknown error>";
    }

    public int getErrorLineNumber() {
        if ( node != null )
            return node.getLineNumber() ;
        else
            return -1;
    }

    public String getErrorSourceFile() {
        if ( node != null )
            return node.getSourceFile() ;
        else
            return "<unknown file>";
    }

    public String getScriptStackTrace()
    {
        if ( callstack == null )
            return "<Unknown>";

        return String.join( "\n", getScriptStackFrames() );
    }

    /**
        The individual "Called from method ..." frame lines for this
        error's frozen call stack, innermost first. Package-private: used
        by TargetError to elide frames already shown by an enclosing
        exception, mirroring java.lang.Throwable's own common-frame
        elision for chained exceptions (see
        TargetError.printTargetError). Empty if there is no callstack or
        no method frames.

        Note on attribution: each namespace's own node records where THAT
        method was called FROM (the call-site, in the caller's body) --
        not anything about its own body. So a frame's location always
        belongs to the *next* (dynamically enclosing) frame, not to the
        method the location's namespace is named after. We shift names by
        one position accordingly; the outermost location (called from
        plain script code, not from within another method) is labeled
        "top level" instead of a method name.
    */
    List<String> getScriptStackFrames()
    {
        List<String> frames = new ArrayList<>();
        if ( callstack == null )
            return frames;

        CallStack stack = callstack.copy();
        List<String> names = new ArrayList<>();
        List<Node> locations = new ArrayList<>();
        while ( stack.depth() > 0 )
        {
            NameSpace ns = stack.pop();
            if ( ns.isMethod )
            {
                names.add( ns.getDisplayName() );
                locations.add( ns.getNode() );
            }
        }

        for ( int i = 0; i < locations.size(); i++ )
        {
            Node node = locations.get(i);
            String callerName = i + 1 < names.size() ? names.get(i+1) : null;
            StringBuilder frame = new StringBuilder();
            frame.append("  Called from ")
                .append( callerName != null ? "method "+callerName : "top level" );
            if ( node != null )
                frame.append(" at line ").append(node.getLineNumber())
                    .append(" in ").append(node.getSourceFile())
                    .append(", near `").append(node.getText()).append("`");
            frames.add( frame.toString() );
        }

        return frames;
    }

    public String getRawMessage() { return message; }

    /**
        Prepend the message if it is non-null.
    */
    protected void prependMessage( String s )
    {
        if ( s == null )
            return;

        if ( message == null )
            message = s;
        else
            message = s + " : "+ message;
    }
}
