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

import java.lang.reflect.InvocationTargetException;
import java.io.PrintStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
    TargetError is an EvalError that wraps an exception thrown by the script
    (or by code called from the script).  TargetErrors indicate exceptions
    which can be caught within the script itself, whereas a general EvalError
    indicates that the script cannot be evaluated further for some reason.

    If the exception is caught within the script it is automatically unwrapped,
    so the code looks like normal Java code.  If the TargetError is thrown
    from the eval() or interpreter.eval() method it may be caught and unwrapped
    to determine what exception was thrown.
*/
public final class TargetError extends EvalError
{
    private final boolean inNativeCode;

    public TargetError(
        String msg, Throwable t, Node node, CallStack callstack,
        boolean inNativeCode )
    {
        super( msg, node, callstack, t );
        this.inNativeCode = inNativeCode;
    }

    public TargetError( Throwable t, Node node, CallStack callstack )
    {
        this("Uncaught Exception", t, node, callstack, false);
    }

    /** The rendered (not live) form of where an exception was originally
     * thrown: plain strings only, derived once at record time from a
     * transient EvalError that is never itself stored. Deliberately NOT a
     * CallStack/EvalError, and deliberately not attached to the exception
     * itself (see #ORIGINAL_LOCATIONS) -- a map value that held the live
     * CallStack would retain whatever NameSpace graph it points to, and if
     * the exception itself was reachable from that graph (e.g. a script
     * does `saved = e;`, or just `catch (e) {}` with `e` visible in the
     * enclosing scope), the value would hold a path straight back to its
     * own key, defeating the WeakHashMap entirely and pinning the
     * Interpreter forever. Plain strings can't reach back to anything. */
    private static final class OriginalLocation {
        final String ownLocation;
        final List<String> scriptStackFrames;
        OriginalLocation( String ownLocation, List<String> scriptStackFrames ) {
            this.ownLocation = ownLocation;
            this.scriptStackFrames = scriptStackFrames;
        }
    }

    /** Records, per exception instance, the script location where it was
     * originally thrown -- populated by BSHTryStatement when it unwraps a
     * caught TargetError, consulted by printTargetError when rendering a
     * message. Deliberately NOT attached to the exception itself (e.g. via
     * addSuppressed()): that would change what script and Java code can
     * observe on an exception they didn't create the wrapping for --
     * polluting getSuppressed() on every catch (not just a catch-and-rewrap),
     * growing without bound on a reused exception instance, and pulling the
     * frozen CallStack (and anything reachable through it) into the
     * exception's serialized form. Keying by identity in a WeakHashMap ties
     * a marker's lifetime to the exception's own reachability -- reusing one
     * instance just overwrites its single entry rather than accumulating,
     * and once the exception itself is unreachable the entry becomes
     * collectible too (swept lazily, on a subsequent map access, like any
     * WeakHashMap) -- provided the value itself holds nothing that can reach
     * back to the key (see OriginalLocation). Wrapped in synchronizedMap
     * since interpreters/exceptions can cross threads and only get/put are
     * used here (no iteration). */
    private static final Map<Throwable, OriginalLocation> ORIGINAL_LOCATIONS =
        Collections.synchronizedMap( new WeakHashMap<>() );

    /** Record where `thrown` was originally thrown in the script. Safe to
     * call repeatedly for the same (possibly reused) exception instance --
     * each call simply replaces its single recorded location. The node/
     * callstack are only used here, transiently, to derive plain strings
     * (see OriginalLocation) -- neither is retained.
     * @param thrown the exception now visible to script/Java code
     * @param rawMessage the original throw site's raw message
     * @param node the original throw site's node
     * @param callstack the original throw site's call stack */
    static void recordOriginalLocation(
            Throwable thrown, String rawMessage, Node node, CallStack callstack ) {
        if ( null != thrown ) {
            EvalError marker = new EvalError( rawMessage, node, callstack );
            ORIGINAL_LOCATIONS.put( thrown,
                new OriginalLocation( marker.getOwnLocation(), marker.getScriptStackFrames() ) );
        }
    }

    public synchronized Throwable getTarget()
    {
        // check for easy mistake
        final Throwable target = getCause();
        if(target instanceof InvocationTargetException)
            return target.getCause();
        else
            return target;
    }

    public synchronized String getMessage()
    {
        return super.getMessage()
            + "\nCaused by: " +
            printTargetError( getCause() );
    }

    public void printStackTrace( boolean debug, PrintStream out ) {
        if ( debug ) {
            super.printStackTrace( out );
            out.println("--- Target Stack Trace ---");
        }
        for ( StackTraceElement ste : getCause().getStackTrace() )
            if ( !isInternalFrame(ste) )
                out.println("        at "+ste);
            else break;
    }

    /** Class/package prefixes marking the point in a wrapped exception's own
     * stack trace where real (script-called) code ends and bsh's reflective
     * invocation machinery begins. Frames from that point on add nothing a
     * reader doesn't already have from the script call-chain frames. */
    private static final String[] INTERNAL_FRAME_PREFIXES = {
        "bsh.", "java.lang.reflect.", "java.lang.invoke.", "jdk.internal.reflect."
    };

    private static boolean isInternalFrame( StackTraceElement ste ) {
        String cls = ste.getClassName();
        for ( String prefix : INTERNAL_FRAME_PREFIXES )
            if ( cls.startsWith(prefix) )
                return true;
        return false;
    }

    /** Render the leading run of `t`'s own Java stack trace -- the frames of
     * real (non-bsh) code that were on the stack when it was thrown -- up to
     * (not including) the point where it re-enters bsh's reflective
     * invocation machinery. Empty when `t` was thrown directly by script
     * code (e.g. `throw new Exception(...)`), since in that case the
     * leading frames are already bsh/reflection internals and carry no
     * information beyond what the script call-chain frames already show
     * (see #inNativeCode).
     * @param t the throwable whose native frames to render
     * @return the frame lines, each already prefixed with "at ", joined by
     *      newlines; empty if there are none */
    private static String nativeStackFrames( Throwable t ) {
        StringBuilder sb = new StringBuilder();
        for ( StackTraceElement ste : t.getStackTrace() ) {
            if ( isInternalFrame(ste) )
                break;
            if ( sb.length() > 0 )
                sb.append("\n");
            sb.append("        at ").append(ste);
        }
        return sb.toString();
    }

    /** A one-line header for a cause (or suppressed exception) in the
     * chain, analogous to Throwable.toString() -- but for an EvalError-typed
     * one, built from its raw message rather than its own (multi-line,
     * self-composing) getMessage(). bsh's own internal wrapping can still
     * put an EvalError-typed object in a real getCause()/getSuppressed()
     * chain (e.g. crossing a reflective invocation boundary) -- without
     * this guard, such an object's own getMessage() (which can itself
     * recurse into a "Caused by:" section) would get embedded inline,
     * silently duplicating information this walk is already printing.
     * Truncated to its first line unconditionally, as a second guard:
     * bsh's own internal wrapping has been observed to bake an
     * already-fully-rendered (multi-line) message into a raw message
     * string at construction time, upstream of anything this class
     * controls -- so even the raw message alone isn't guaranteed short.
     * @param cur the throwable to render a header for
     * @return a single-line description of `cur` */
    private static String causeHeader( Throwable cur ) {
        String header = cur instanceof EvalError
            ? cur.getClass().getName() + ": " + ((EvalError) cur).getRawMessage()
            : cur.toString();
        int newline = header.indexOf('\n');
        return newline < 0 ? header : header.substring(0, newline);
    }

    /** Generate a printable string showing the wrapped target exceptions.
     * Also surfaces, for any throwable in the cause chain:
     * - the leading run of that throwable's own Java stack trace that is
     *   real (non-bsh) code, i.e. what was on the stack when it was
     *   thrown, before unwinding back into bsh's own reflective
     *   invocation machinery (see #nativeStackFrames) -- since that part
     *   of the trace is information the script call-chain frames can't
     *   provide.
     * - the original bsh script trace of an exception that was caught
     *   and discarded at that point (recorded in #ORIGINAL_LOCATIONS by
     *   BSHTryStatement) -- so information about where an exception was
     *   originally thrown is not lost when a catch/finally block throws a
     *   new exception wrapping it. Call-chain frames already shown by
     *   this (enclosing) exception's own trace are elided from the
     *   "Originally thrown" frames, mirroring java.lang.Throwable's own
     *   common-frame elision for chained exceptions.
     * - any genuine suppressed exception (e.g. a real failure from a
     *   try-with-resources close()) under a plain "Suppressed:" label,
     *   mirroring java.lang.Throwable's own convention -- without this,
     *   such exceptions are silently invisible to getMessage() (only
     *   printStackTrace() shows suppressed exceptions by default).
     * @param t wrapped target exception
     * @return messages unwrapped */
    private synchronized String printTargetError( Throwable t ) {
        if (null == t) return "Cause is null";
        StringBuilder msgs = new StringBuilder();
        boolean first = true;
        for ( Throwable cur = t; cur != null; cur = cur.getCause() ) {
            // The first cause's "Caused by: " label is prepended once by
            // getMessage() (it directly follows this exception's own
            // location); every deeper cause needs its own label here,
            // matching java.lang.Throwable's own convention of labeling
            // each level of the chain, not just the first.
            msgs.append( first ? "" : "\nCaused by: " );
            msgs.append(causeHeader(cur));
            first = false;
            String nativeFrames = nativeStackFrames(cur);
            if ( !nativeFrames.isEmpty() )
                msgs.append("\n").append(nativeFrames);

            OriginalLocation original = ORIGINAL_LOCATIONS.get(cur);
            // Skip when the recorded location is identical to this
            // exception's own -- e.g. when the exception simply escaped
            // the try block untouched (rethrown as the original error,
            // see BSHTryStatement), the marker adds nothing beyond what
            // the top-level location already shows. It's only
            // informative when a catch/finally block wrapped the
            // original in a genuinely new, differently-located exception.
            if ( null != original
                    && !original.ownLocation.equals( this.getOwnLocation() ) ) {
                msgs.append("\n  Originally thrown").append(original.ownLocation);
                String frames = elideCommonFrames(
                    original.scriptStackFrames, this.getScriptStackFrames() );
                if ( !frames.isEmpty() )
                    msgs.append("\n").append(frames);
            }

            for ( Throwable sup : cur.getSuppressed() ) {
                msgs.append("\n  Suppressed: ").append(causeHeader(sup));
                String supFrames = nativeStackFrames(sup);
                if ( !supFrames.isEmpty() )
                    msgs.append("\n").append(supFrames);
            }
        }
        return msgs.toString();
    }

    /** Render `frames` (innermost first) eliding a trailing run that
     * exactly matches the trailing run of `enclosingFrames`, replacing
     * the elided lines with a single "... N more" line -- mirrors
     * java.lang.Throwable's own stack trace elision for chained
     * exceptions, applied to bsh's script call-stack frames.
     * @param frames the (possibly elidable) frame lines, innermost first
     * @param enclosingFrames the already-shown enclosing frame lines
     * @return the frame lines to print, joined by newlines */
    private static String elideCommonFrames( List<String> frames, List<String> enclosingFrames ) {
        int m = frames.size(), n = enclosingFrames.size();
        int common = 0;
        while ( common < m && common < n
                && frames.get(m-1-common).equals(enclosingFrames.get(n-1-common)) )
            common++;

        StringBuilder sb = new StringBuilder();
        for ( int i = 0; i < m - common; i++ ) {
            if ( sb.length() > 0 )
                sb.append("\n");
            sb.append(frames.get(i));
        }
        if ( common > 0 ) {
            if ( sb.length() > 0 )
                sb.append("\n");
            sb.append("  ... ").append(common).append(" more");
        }
        return sb.toString();
    }

    /**
        Return true if the TargetError was generated from native code.
        e.g. if the script called into a compiled java class which threw
        the excpetion.  We distinguish so that we can print the stack trace
        for the native code case... the stack trace would not be useful if
        the exception was generated by the script.  e.g. if the script
        explicitly threw an exception... (the stack trace would simply point
        to the bsh internals which generated the exception).
    */
    public boolean inNativeCode() {
        return inNativeCode;
    }
}

