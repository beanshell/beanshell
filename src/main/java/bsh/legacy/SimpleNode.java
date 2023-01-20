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
package bsh.legacy;

import bsh.congo.parser.Node;
import bsh.congo.parser.BaseNode;
import java.io.Serializable;
import java.util.NoSuchElementException;
import java.util.List;
import java.util.ArrayList;

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
public class SimpleNode extends BaseNode implements Serializable {

    /** Serialization ID */
    private static final long serialVersionUID = 1L;

    /** The first and last tokens */
    Token firstToken, lastToken;

    /** the source of the text from which this was parsed */
    private String sourceFile;

    private Node[] nodes = new Node[0];
    private int cursor = 0, lastRet = -1;

    private void updateBackingContainer() {
        List<Node> nodeList = new ArrayList<>();
        for (Node n : getNodes()) {
            nodeList.add(n);
        }
        setBackingContainer(nodeList);
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
        return nodes[lastRet = cursor++];
    }

    /** {@inheritDoc} */
    @Override
    public Node previous() {
        if (!hasPrevious()) throw new NoSuchElementException();
        return nodes[lastRet = --cursor];
    }

    /** {@inheritDoc} */
    @Override
    public void remove() {
        if (lastRet < 0) throw new IllegalStateException();
        cursor = lastRet;
        Node c[] = new Node[nodes.length - 1];
        System.arraycopy(nodes, 0, c, 0, cursor);
        System.arraycopy(nodes, cursor + 1, c, cursor, c.length - cursor);
        nodes = c;
        updateBackingContainer();
        lastRet = -1;
    }

    /** {@inheritDoc} */
    @Override
    public void set(Node e) {
        if (lastRet < 0) throw new IllegalStateException();
        nodes[lastRet] = e;
        updateBackingContainer();
    }

    /** {@inheritDoc} */
    @Override
    public void add(Node e) {
        Node c[] = new Node[getChildCount() + 1];
        System.arraycopy(nodes, 0, c, 0, cursor);
        System.arraycopy(nodes, cursor, c, cursor +1, c.length - cursor -1);
        nodes = c;
        nodes[cursor++] = e;
        lastRet = -1;
        e.setParent(this);
        updateBackingContainer();
    }

    /** {@inheritDoc} */
    @Override
    public void addChild(int i, Node n) {
        if (nodes == null)
            nodes = new Node[i + 1];
        else if (i >= nodes.length) {
            Node c[] = new Node[i + 1];
            System.arraycopy(nodes, 0, c, 0, nodes.length);
            nodes = c;
        }
        nodes[i] = n;
        updateBackingContainer();
    }

    /** {@inheritDoc} */
    @Override
    public Node getChild(int i) { return nodes[i]; }

    /** {@inheritDoc} */
    @Override
    final public List<Node> getNodes() {
        List<Node> result = new ArrayList<>();
        for (Node n : nodes) {
            result.add(n);
        }
        return result;
        //return nodes;
    }
    /** {@inheritDoc} */
    @Override
    final public int getChildCount() {
        return nodes.length;
//        return getNodes().length;
//        return getBackingContainer().size();
    }

    protected void setNodes(Node... nodes) {
        this.nodes = nodes;
        updateBackingContainer();
    }

    /** {@inheritDoc} */
    @Override
    //public String toString() { return ParserTreeConstants.jjtNodeName[id]; }
    public String toString() {return getClass().getSimpleName().substring(3);}

    /** {@inheritDoc} */
    @Override
    public String toString(String prefix) { return prefix + toString(); }

    /** {@inheritDoc} */
    @Override
    public void dump(String prefix) {
        System.out.println(toString(prefix));
        if (nodes != null) for (int i = 0; i < nodes.length; ++i) {
            Node n = nodes[i];
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
        if ( sourceFile == null )
            if ( getParent() != null )
                return getParent().getSourceFile();
            else
                return "<unknown file>";
        else
            return sourceFile;
    }

    /** {@inheritDoc} */
    @Override
    public int getLineNumber() { return firstToken.beginLine; }

    /** {@inheritDoc} */
    @Override
    public String getText()
    {
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

