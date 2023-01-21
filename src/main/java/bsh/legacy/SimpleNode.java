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

public class SimpleNode extends BaseNode implements Serializable {

    /** Serialization ID */
    private static final long serialVersionUID = 1L;

    /** The first and last tokens */
    Token firstToken, lastToken;

    /** the source of the text from which this was parsed */
    private String sourceFile;

    /** {@inheritDoc} */
    @Override
    public String toString() {return getClass().getSimpleName().substring(3);}

    /** {@inheritDoc} */
    @Override
    public String toString(String prefix) { return prefix + toString(); }

    /** {@inheritDoc} */
    @Override
    public void dump(String prefix) {
        System.out.println(toString(prefix));
        for (Node n : children()) {
            if (n != null) n.dump(prefix + " ");
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

