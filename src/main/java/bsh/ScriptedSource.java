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

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A parsed scripted method declaration or anonymous allocation expression,
 * "new Type(args) { ... }", without exposing the syntax tree.
 * <p>
 * Obtain one from an existing definition with {@link #of(BshMethod)} or
 * {@link #of(AllocationInfo)}, or by parsing text with {@link #parseMethod(String)}
 * or {@link #parseAllocation(String)}.
 * <p>
 * Where text is "normalized" (see {@link #name()}, {@link #parameterText()} and
 * {@link #nonMethodStatements()}) it is the parser's tokens joined by single spaces,
 * without comments. For example the parameters of <code>m(Object a, int b)</code> are
 * <code>( Object a , int b )</code>. That text is meant for <em>comparing</em> two sources
 * for equality, not for display, and its exact format is not specified. Use
 * {@link #sourceText()} to show a source to a user.
 */
public final class ScriptedSource {

    /** The kind of construct a source describes. */
    public enum Kind {
        /** A method declaration, for example <code>Callable make(Object a) { ... return this; }</code>. */
        METHOD,
        /** An anonymous class allocation, <code>new Type(args) { ... }</code>. */
        ANONYMOUS_CLASS
    }

    /**
     * A method declaration found in the body of a {@link ScriptedSource}, with just
     * enough information to tell declarations apart. Text is normalized as
     * described in {@link ScriptedSource}.
     */
    public static final class Member {
        private final BSHMethodDeclaration declaration;

        Member(BSHMethodDeclaration declaration) { this.declaration = declaration; }

        /** The method name. */
        public String name() { return declaration.name; }

        /** The normalized formal parameters. */
        public String parameterText() { return paramText(declaration); }

        /** The number of formal parameters. */
        public int parameterCount() {
            BSHFormalParameters params = child(declaration, BSHFormalParameters.class);
            int count = 0;
            if ( params != null )
                for ( int i = 0; i < params.jjtGetNumChildren(); i++ )
                    if ( params.jjtGetChild(i) instanceof BSHFormalParameter )
                        count++;
            return count;
        }

        /** Name followed by the normalized parameters; unique per overload in a body. */
        public String signature() { return name() + parameterText(); }

        BSHMethodDeclaration declaration() { return declaration; }

        @Override
        public String toString() { return signature(); }
    }

    private final Kind kind;
    private final SimpleNode node;

    private ScriptedSource(Kind kind, SimpleNode node) {
        this.kind = kind;
        this.node = node;
    }

    // ---- creating ------------------------------------------------------

    /**
     * The declaration a method was created from.
     * @throws IllegalArgumentException if the method has no declaration (e.g. a Java method)
     */
    public static ScriptedSource of(BshMethod method) {
        BSHMethodDeclaration declaration = method.getDeclaration();
        if ( declaration == null )
            throw new IllegalArgumentException(
                "Method has no scripted declaration: " + method.getName());
        return new ScriptedSource(Kind.METHOD, declaration);
    }

    /** The expression an anonymous class instance was allocated by. */
    public static ScriptedSource of(AllocationInfo allocation) {
        return new ScriptedSource(Kind.ANONYMOUS_CLASS, allocation.getNode());
    }

    /**
     * Parse text that must contain exactly one method declaration.
     * @throws ParseException if the text is empty, has more than one statement, or
     *   is not a method declaration
     */
    public static ScriptedSource parseMethod(String text) throws ParseException {
        SimpleNode node = parseSingle(text);
        if ( !(node instanceof BSHMethodDeclaration) )
            throw new ParseException("Text must be a single method declaration");
        return new ScriptedSource(Kind.METHOD, node);
    }

    /**
     * Parse text that must contain exactly one "new Type(...) { ... }" expression.
     * A trailing ';' is optional.
     * @throws ParseException if the text is empty, has more than one statement, or
     *   is not an allocation with a class body
     */
    public static ScriptedSource parseAllocation(String text) throws ParseException {
        String terminated = text.trim();
        if ( !terminated.endsWith(";") )
            terminated += ";";
        BSHAllocationExpression allocation = allocationIn(parseSingle(terminated));
        if ( allocation == null || bodyOf(allocation) == null )
            throw new ParseException(
                "Text must be a single 'new Type(...) { ... }' expression");
        return new ScriptedSource(Kind.ANONYMOUS_CLASS, allocation);
    }

    // ---- inspecting ----------------------------------------------------

    /** Whether this is a method declaration or an anonymous class allocation. */
    public Kind kind() { return kind; }

    /** The source as written, including comments; see {@link SimpleNode#getSourceText()}. */
    public String sourceText() { return node.getSourceText(); }

    /**
     * The method name, or for an allocation the type it names, as normalized text.
     * The text of a qualified type name may contain spaces, because it is built from
     * the parser's tokens. Two sources with the same kind, name and parameter text
     * are the same declaration or allocation as far as an edit is concerned. Not
     * meant for display.
     */
    public String name() {
        return kind == Kind.METHOD
            ? ((BSHMethodDeclaration) node).name
            : norm((SimpleNode) node.jjtGetChild(0));
    }

    /**
     * The formal parameters of a method, or the constructor argument
     * <em>expressions</em> of an allocation (the text, not the values), including the
     * parentheses, as normalized text such as <code>( Object a , int b )</code>.
     * It is for comparing sources, so that an edit can be checked to leave them unchanged.
     * The exact format is not specified and it is not meant for display.
     */
    public String parameterText() {
        return kind == Kind.METHOD
            ? paramText((BSHMethodDeclaration) node)
            : norm(child(node, BSHArguments.class));
    }

    /** Whether there is a body (a method may be declared without one). */
    public boolean hasBody() { return bodyOf(node) != null; }

    /** Normalized text of each statement in the body that is not a method declaration. */
    public List<String> nonMethodStatements() {
        List<String> out = new ArrayList<>();
        BSHBlock body = bodyOf(node);
        if ( body != null )
            for ( int i = 0; i < body.jjtGetNumChildren(); i++ )
                if ( !(body.jjtGetChild(i) instanceof BSHMethodDeclaration) )
                    out.add(norm((SimpleNode) body.jjtGetChild(i)));
        return out;
    }

    /** The method declarations directly in the body, in source order. */
    public List<Member> methods() {
        List<Member> out = new ArrayList<>();
        BSHBlock body = bodyOf(node);
        if ( body != null )
            for ( int i = 0; i < body.jjtGetNumChildren(); i++ )
                if ( body.jjtGetChild(i) instanceof BSHMethodDeclaration )
                    out.add(new Member((BSHMethodDeclaration) body.jjtGetChild(i)));
        return Collections.unmodifiableList(out);
    }

    // ---- evaluating ----------------------------------------------------

    /**
     * Evaluate this method declaration into a namespace.
     * @return the method that was defined
     * @throws IllegalStateException if this is not a method declaration
     */
    public BshMethod defineIn(NameSpace namespace, Interpreter interpreter) throws EvalError {
        requireKind(Kind.METHOD);
        BSHMethodDeclaration declaration = (BSHMethodDeclaration) node;
        declaration.eval(new CallStack(namespace), interpreter);
        for ( BshMethod method : namespace.getMethods() )
            if ( method.getDeclaration() == declaration )
                return method;
        throw new IllegalStateException("Method " + declaration.name + " was not defined");
    }

    /** Evaluate every method declaration in the body into a namespace. */
    public void defineMethodsIn(NameSpace namespace, Interpreter interpreter) throws EvalError {
        CallStack callstack = new CallStack(namespace);
        for ( Member member : methods() )
            member.declaration().eval(callstack, interpreter);
    }

    /**
     * Construct a new instance from this allocation's class body, using
     * already evaluated arguments, which are not evaluated again. The new
     * instance gets its own {@link AllocationInfo}, recorded on its namespace, with
     * <code>scope</code> as its scope.
     * <p>
     * The <code>type</code> is not compared with the type named in this source.
     * The caller must pass the interface or superclass this allocation is for.
     * @param type the interface or superclass to implement or extend
     * @param args the constructor arguments, as {@link AllocationInfo#getArgs()}
     *   returns them (primitive values wrapped in {@link Primitive}); use an empty
     *   array for an interface
     * @param scope the namespace the anonymous class is defined in; variables
     *   visible from it are visible in the class body
     * @return the new instance: a proxy for an interface, or an instance of a
     *   generated class; see {@link This#of(Object)}
     * @throws IllegalStateException if this is not an anonymous allocation
     * @throws EvalError if constructing the instance fails
     */
    public Object constructAnonymous(Class<?> type, Object[] args, NameSpace scope,
            Interpreter interpreter) throws EvalError {
        return construct(type, new CallArguments(args), scope, interpreter);
    }

    /**
     * Construct a new instance from this allocation's class body with the type and
     * arguments (including their declared types) recorded in an earlier allocation,
     * so the original argument values are used and not evaluated again. This source
     * may be an edited version of the one the earlier allocation came from. The new
     * instance gets its own {@link AllocationInfo}, with <code>scope</code> as its scope.
     * @param original the earlier allocation, from {@link NameSpace#getAllocation()}
     * @param scope the namespace the anonymous class is defined in
     * @return the new instance; see {@link #constructAnonymous(Class, Object[], NameSpace, Interpreter)}
     * @throws IllegalStateException if this is not an anonymous allocation
     * @throws EvalError if constructing the instance fails
     */
    public Object constructAnonymous(AllocationInfo original, NameSpace scope,
            Interpreter interpreter) throws EvalError {
        return construct(original.getType(), original.getCallArguments(), scope, interpreter);
    }

    private Object construct(Class<?> type, CallArguments arguments, NameSpace scope,
            Interpreter interpreter) throws EvalError {
        requireKind(Kind.ANONYMOUS_CLASS);
        return allocationNode().constructAnonymous(
            type, arguments, bodyOf(node), new CallStack(scope), interpreter);
    }

    BSHAllocationExpression allocationNode() {
        requireKind(Kind.ANONYMOUS_CLASS);
        return (BSHAllocationExpression) node;
    }

    private void requireKind(Kind required) {
        if ( kind != required )
            throw new IllegalStateException("Not a " + required + " source: " + kind);
    }

    @Override
    public String toString() { return kind + " " + name() + parameterText(); }

    // ---- helpers -------------------------------------------------------

    /** Parse text that must contain exactly one statement or declaration. */
    private static SimpleNode parseSingle(String text) throws ParseException {
        Parser parser = new Parser(new StringReader(text));
        if ( parser.Line() )
            throw new ParseException("Text is empty");
        SimpleNode node = parser.popNode();
        if ( !parser.Line() )
            throw new ParseException("Text must contain a single declaration or expression");
        return node;
    }

    /** The allocation itself, possibly wrapped in single-child nodes. */
    private static BSHAllocationExpression allocationIn(SimpleNode node) {
        while ( node != null ) {
            if ( node instanceof BSHAllocationExpression )
                return (BSHAllocationExpression) node;
            if ( node.jjtGetNumChildren() != 1 )
                return null;
            node = (SimpleNode) node.jjtGetChild(0);
        }
        return null;
    }

    private static <T> T child(SimpleNode node, Class<T> type) {
        for ( int i = 0; i < node.jjtGetNumChildren(); i++ ) {
            Object child = node.jjtGetChild(i);
            if ( type.isInstance(child) )
                return type.cast(child);
        }
        return null;
    }

    private static BSHBlock bodyOf(SimpleNode declarationOrAllocation) {
        return child(declarationOrAllocation, BSHBlock.class);
    }

    private static String norm(SimpleNode node) {
        return node == null ? "" : node.getText().replaceAll("\\s+", " ").trim();
    }

    private static String paramText(BSHMethodDeclaration declaration) {
        return norm(child(declaration, BSHFormalParameters.class));
    }
}
