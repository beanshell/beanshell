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

    public void addChild(Node n) {
        super.addChild(n);
        Node[] newNodes = new Node[nodes.length+1];
        for (int i = 0; i< nodes.length; i++) {
            newNodes[i] = nodes[i];
        }
        newNodes[nodes.length] = n;
        this.nodes = newNodes;
    }

    /** {@inheritDoc} */
    @Override
    public void addChild(int i, Node n) {
        if (i>nodes.length) {
            throw new ArrayIndexOutOfBoundsException();
        }
        else if (i == nodes.length) {
            Node c[] = new Node[nodes.length + 1];
            for (int j = 0; j< nodes.length; j++) {
                c[j] = nodes[j];
            }
            nodes = c;
            nodes[i] = n;
            super.addChild(i, n);
        }
        else {
           nodes[i] = n;
           setChild(i, n);
        }
    }

    public Node removeChild(int i) {
        Node[] newNodes = new Node[nodes.length-1];
        for (int j = 0; j < i; j++) {
            newNodes[j] = nodes[j];
        }
        for (int j = i + 1; j < nodes.length; j++) {
            newNodes[j-1] = nodes[j];
        }
        this.nodes = newNodes;
        return super.removeChild(i);        
    }

    /** {@inheritDoc} */
    @Override
    public Node getChild(int i) { return nodes[i]; }

    /** {@inheritDoc} */
    //@Override
    final public List<Node> getNodes() {
        List<Node> result = new ArrayList<>();
        for (Node n : nodes) {
            result.add(n);
        }
        return result;
    }
    /** {@inheritDoc} */
    @Override
    final public int getChildCount() {
//        if (super.getChildCount() != nodes.length) {
//            System.err.println("KILROY: " + getClass().getSimpleName());
//        }
        return nodes.length;
//        return getNodes().length;
    }

    protected void setNodes(Node... nodes) {
//        this.nodes = nodes;
        clearChildren();
        for (Node n : nodes) {
            addChild(n);
        }
    }

    public void clearChildren() {
        super.clearChildren();
        nodes = new Node[0];
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

