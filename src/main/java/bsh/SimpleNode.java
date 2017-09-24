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

/**
 * The Class SimpleNode.
 *
 * Note: great care (and lots of typing) were taken to insure that the
 * namespace and interpreter references are passed on the stack and not
 * (as they were erroneously before) installed in instance variables...
 * Each of these node objects must be re-entrable to allow for recursive
 * situations.
 * The only data which should really be stored in instance vars here should
 * be parse tree data... features of the node which should never change (e.g.
 * the number of arguments, etc.)
 * Exceptions would be public fields of simple classes that just publish
 * data produced by the last eval()... data that is used immediately. We'll
 * try to remember to mark these as transient to highlight them.
 */
class SimpleNode implements Node {

    /** The Constant serialVersionUID. */
    private static final long serialVersionUID = 1L;
    /** The javacode. */
    public static SimpleNode JAVACODE = new SimpleNode(-1) {

        /**
         *
         */
        private static final long serialVersionUID = 1L;

        @Override
        public String getSourceFile() {
            return "<Called from Java Code>";
        }

        @Override
        public int getLineNumber() {
            return -1;
        }

        @Override
        public String getText() {
            return "<Compiled Java Code>";
        }
    };
    /** The parent. */
    protected Node parent;
    /** The children. */
    protected Node[] children;
    /** The id. */
    protected int id;
    /** The last token. */
    Token firstToken, lastToken;
    /** the source of the text from which this was parsed. */
    String sourceFile;

    /**
     * Instantiates a new simple node.
     *
     * @param i
     *            the i
     */
    public SimpleNode(final int i) {
        this.id = i;
    }

    /** {@inheritDoc} */
    public void jjtOpen() {}

    /** {@inheritDoc} */
    public void jjtClose() {}

    /** {@inheritDoc} */
    public void jjtSetParent(final Node n) {
        this.parent = n;
    }

    /** {@inheritDoc} */
    public Node jjtGetParent() {
        return this.parent;
    }
    // public SimpleNode getParent() { return (SimpleNode)parent; }

    /** {@inheritDoc} */
    public void jjtAddChild(final Node n, final int i) {
        if (this.children == null)
            this.children = new Node[i + 1];
        else if (i >= this.children.length) {
            final Node c[] = new Node[i + 1];
            System.arraycopy(this.children, 0, c, 0, this.children.length);
            this.children = c;
        }
        this.children[i] = n;
    }

    /** {@inheritDoc} */
    public Node jjtGetChild(final int i) {
        return this.children[i];
    }

    /**
     * Gets the child.
     *
     * @param i
     *            the i
     * @return the child
     */
    public SimpleNode getChild(final int i) {
        return (SimpleNode) this.jjtGetChild(i);
    }

    /** {@inheritDoc} */
    public int jjtGetNumChildren() {
        return this.children == null ? 0 : this.children.length;
    }

    /** {@inheritDoc} *
     * You can override these two methods in subclasses of SimpleNode to
     * customize the way the node appears when the tree is dumped. If
     * your output uses more than one line you should override
     * toString(String), otherwise overriding toString() is probably all
     * you need to do.
     */
    @Override
    public String toString() {
        return ParserTreeConstants.jjtNodeName[this.id];
    }

    /**
     * To string.
     *
     * @param prefix
     *            the prefix
     * @return the string
     */
    public String toString(final String prefix) {
        return prefix + this.toString();
    }

    /**
     * Dump.
     *
     * @param prefix
     *            the prefix
     *
     * Override this method if you want to customize how the node dumps
     * out its children.
     */
    public void dump(final String prefix) {
        System.out.println(this.toString(prefix));
        if (this.children != null)
            for (final Node element : this.children) {
                final SimpleNode n = (SimpleNode) element;
                if (n != null)
                    n.dump(prefix + " ");
            }
    }

    // ---- BeanShell specific stuff hereafter ---- //
    /**
     * Detach this node from its parent.
     * This is primarily useful in node serialization.
     * (see BSHMethodDeclaration)
     */
    public void prune() {
        this.jjtSetParent(null);
    }

    /**
     * This is the general signature for evaluation of a node.
     *
     * @param callstack
     *            the callstack
     * @param interpreter
     *            the interpreter
     * @return the object
     * @throws EvalError
     *             the eval error
     */
    public Object eval(final CallStack callstack, final Interpreter interpreter)
            throws EvalError {
        throw new InterpreterError("Unimplemented or inappropriate for "
                + this.getClass().getName());
    }

    /**
     * Set the name of the source file (or more generally source) of
     * the text from which this node was parsed.
     *
     * @param sourceFile
     *            the new source file
     */
    public void setSourceFile(final String sourceFile) {
        this.sourceFile = sourceFile;
    }

    /**
     * Get the name of the source file (or more generally source) of
     * the text from which this node was parsed.
     * This will recursively search up the chain of parent nodes until
     * a source is found or return a string indicating that the source
     * is unknown.
     *
     * @return the source file
     */
    public String getSourceFile() {
        if (this.sourceFile == null)
            if (this.parent != null)
                return ((SimpleNode) this.parent).getSourceFile();
            else
                return "<unknown file>";
        else
            return this.sourceFile;
    }

    /**
     * Get the line number of the starting token.
     *
     * @return the line number
     */
    public int getLineNumber() {
        return this.firstToken.beginLine;
    }

    /**
     * Get the ending line number of the starting token.
     * public int getEndLineNumber() {
     * return lastToken.endLine;
     * }
     */

     /**
     * Get the text of the tokens comprising this node.
     *
     * @return the text
     */
    public String getText() {
        final StringBuffer text = new StringBuffer();
        Token t = this.firstToken;
        while (t != null) {
            text.append(t.image);
            if (!t.image.equals("."))
                text.append(" ");
            if (t == this.lastToken || t.image.equals("{")
                    || t.image.equals(";"))
                break;
            t = t.next;
        }
        return text.toString();
    }
}
