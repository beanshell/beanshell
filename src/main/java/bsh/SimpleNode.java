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
class SimpleNode implements Node, Serializable {

    /** Serialization ID */
    private static final long serialVersionUID = 1L;

    public static SimpleNode JAVACODE =
        new SimpleNode( -1 ) {
            public String getSourceFile() {
                return "<Called from Java Code>";
            }

            public int getLineNumber() {
                return -1;
            }

            public String getText()  {
                return "<Compiled Java Code>";
            }
        };

    protected Node parent;
    protected Node[] children;
    protected int id;
    protected Object value;
    protected Parser parser;

    Token firstToken, lastToken;

    /** the source of the text from which this was parsed */
    String sourceFile;

    public SimpleNode(int i) {
        id = i;
    }

    public SimpleNode(Parser p, int i) {
      this(i);
      parser = p;
    }

    public void jjtOpen() { }
    public void jjtClose() { }

    public void jjtSetParent(Node n) { parent = n; }
    public Node jjtGetParent() { return parent; }
    //public SimpleNode getParent() { return (SimpleNode)parent; }

    public void jjtAddChild(Node n, int i)
    {
        if (children == null)
            children = new Node[i + 1];
        else
            if (i >= children.length)
            {
                Node c[] = new Node[i + 1];
                System.arraycopy(children, 0, c, 0, children.length);
                children = c;
            }

        children[i] = n;
    }

    public Node jjtGetChild(int i) {
        return children[i];
    }
    public SimpleNode getChild( int i ) {
        return (SimpleNode)jjtGetChild(i);
    }

    public int jjtGetNumChildren() {
        return (children == null) ? 0 : children.length;
    }

    public void jjtSetValue(Object value) { this.value = value; }
    public Object jjtGetValue() { return value; }

    /*
        You can override these two methods in subclasses of SimpleNode to
        customize the way the node appears when the tree is dumped.  If
        your output uses more than one line you should override
        toString(String), otherwise overriding toString() is probably all
        you need to do.
    */
    public String toString() { return ParserTreeConstants.jjtNodeName[id]; }
    public String toString(String prefix) { return prefix + toString(); }

    /*
        Override this method if you want to customize how the node dumps
        out its children.
    */
    public void dump(String prefix)
    {
        System.out.println(toString(prefix));
        if(children != null)
        {
            for(int i = 0; i < children.length; ++i)
            {
                SimpleNode n = (SimpleNode)children[i];
                if (n != null)
                {
                    n.dump(prefix + " ");
                }
            }
        }
    }

    //  ---- BeanShell specific stuff hereafter ----  //

    /**
        Detach this node from its parent.
        This is primarily useful in node serialization.
        (see BSHMethodDeclaration)
    */
    public void prune() {
        jjtSetParent( null );
    }

    /**
        This is the general signature for evaluation of a node.
    */
    public Object eval( CallStack callstack, Interpreter interpreter )
        throws EvalError
    {
        throw new InterpreterError(
            "Unimplemented or inappropriate for " + getClass().getName() );
    }

    /**
        Set the name of the source file (or more generally source) of
        the text from which this node was parsed.
    */
    public void setSourceFile( String sourceFile ) {
        this.sourceFile = sourceFile;
    }

    /**
        Get the name of the source file (or more generally source) of
        the text from which this node was parsed.
        This will recursively search up the chain of parent nodes until
        a source is found or return a string indicating that the source
        is unknown.
    */
    public String getSourceFile() {
        if ( sourceFile == null )
            if ( parent != null )
                return ((SimpleNode)parent).getSourceFile();
            else
                return "<unknown file>";
        else
            return sourceFile;
    }

    /**
        Get the line number of the starting token
    */
    public int getLineNumber() {
        return firstToken.beginLine;
    }

    /**
        Get the ending line number of the starting token
    public int getEndLineNumber() {
        return lastToken.endLine;
    }
    */

    /**
        Get the text of the tokens comprising this node.
    */
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

    @Override
    public int getId() {
        return this.id;
    }
}

