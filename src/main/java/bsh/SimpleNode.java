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

import java.io.Serializable;
import java.util.NoSuchElementException;
import bsh.congo.parser.BaseNode;

/*
    Note: great care (and lots of typing) were taken to insure that the
    namespace and interpreter references are passed on the stack and not
    (as they were erroneously before) installed in instance variables...
    Each of these node objects must be re-entrable to allow for recursive
    situations.

    The only data which should really be stored in instance vars here should
    be parse tree data... features of the node which should never change (e.g.
    the number of arguments, etc.)

    Exceptions would be public fields of simple classes that just publish
    data produced by the last eval()... data that is used immediately. We'll
    try to remember to mark these as transient to highlight them.
*/
public class SimpleNode extends BaseNode implements Node, Serializable {

    /** Serialization ID */
    private static final long serialVersionUID = 1L;

    /** The first and last tokens */
    Token firstToken, lastToken;

    /** the source of the text from which this was parsed */
    private String sourceFile;

    private int cursor = 0, lastRet = -1;

    private bsh.congo.parser.Node wrappedNode;

    public SimpleNode() {}

    public SimpleNode(int id) {}

    public SimpleNode(bsh.congo.parser.Node wrappedNode) {
        this.wrappedNode = wrappedNode;
    }

    /** {@inheritDoc} */
    @Override
    public boolean hasPrevious() { return cursor > 0; }

    /** {@inheritDoc} */
    @Override
    public boolean hasNext() { return cursor < getChildCount(); }

    /** {@inheritDoc} */
    @Override
    public int nextIndex() { return cursor; }

    /** {@inheritDoc} */
    @Override
    public int previousIndex() { return cursor - 1; }

    /** {@inheritDoc} */
    @Override
    public Node next() {
        if (!hasNext()) throw new NoSuchElementException();
        //return children[lastRet = cursor++];
        return (Node) getChild(lastRet = cursor++);
    }

    /** {@inheritDoc} */
    @Override
    public Node previous() {
        if (!hasPrevious()) throw new NoSuchElementException();
        return (Node) getChild(lastRet = --cursor);
    }

    /** {@inheritDoc} */
    @Override
    public void remove() {
        if (lastRet < 0) throw new IllegalStateException();
        cursor = lastRet;
        removeChild(cursor);
        lastRet = -1;
    }

    /** {@inheritDoc} */
    @Override
    public void set(Node e) {
        if (lastRet < 0) throw new IllegalStateException();
        //children[lastRet] = e;
        setChild(lastRet, (bsh.congo.parser.Node) e);
    }

    /** {@inheritDoc} */
    @Override
    public void add(Node e) {
        addChild((bsh.congo.parser.Node) e);
        lastRet = -1;
    }

    /** {@inheritDoc} */
    @Override
    public String toString() { return getClass().getSimpleName();}

    /** {@inheritDoc} */
    @Override
    public String toString(String prefix) { return prefix + toString(); }

    /** {@inheritDoc} */
    @Override
    public void dump(String prefix) {
        System.out.println(toString(prefix));
        for (int i = 0; i < getChildCount(); ++i) {
            Node n = (Node) getChild(i);
            if (n != null)
                n.dump(prefix + " ");
        }
    }

    /** {@inheritDoc} */
    @Override
    public void setSourceFile(String sourceFile) {
        this.sourceFile = sourceFile;
    }

    /** {@inheritDoc} */
    @Override
    public String getSourceFile() {
        if (wrappedNode != null) {
            return wrappedNode.getInputSource();
        }
        if ( sourceFile == null )
            if ( parent != null )
                return getParent().getSourceFile();
            else
                return "<unknown file>";
        else
            return sourceFile;
    }

    /** {@inheritDoc} */
    @Override
    public int getLineNumber() { 
        if (wrappedNode != null) return wrappedNode.getBeginLine();
        return firstToken.beginLine; 
    }

    /** {@inheritDoc} */
    @Override
    public String getText()
    {
        if (wrappedNode != null) return wrappedNode.getSource();
        StringBuilder text = new StringBuilder();
        Token t = firstToken;
        while ( t!=null ) {
            text.append(t.image);
            if ( !t.image.equals(".") )
                text.append(" ");
            if ( t==lastToken ||
                t.image.equals("{") || t.image.equals(";") )
                break;
            t=t.next;
        }

        return text.toString();
    }
}

