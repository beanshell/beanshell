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

import bsh.legacy.*;
import bsh.congo.parser.Node;
import java.util.Iterator;

/**
 * Implementation of the enhanced for(:) statement.
 *  This statement uses Iterator to support iteration over a wide variety
 *  of iterable types.
 *
 * @author Daniel Leuck
 * @author Pat Niemeyer
 */
public class BSHEnhancedForStatement extends SimpleNode implements ParserConstants {

    final int blockId;
    public String varName, label;
    public boolean isFinal = false;


    public BSHEnhancedForStatement(int id) {
        super(id);
        blockId = BlockNameSpace.blockCount.incrementAndGet();
    }


    public Object eval(CallStack callstack, Interpreter interpreter) throws EvalError {
        Modifiers modifiers = new Modifiers(Modifiers.PARAMETER);
        if (this.isFinal)
            modifiers.addModifier("final");
        Class<?> elementType = null;
        final Node expression;
        final Node statement;
        final NameSpace enclosingNameSpace = callstack.top();
        final Node firstNode = getChild(0);
        final int nodeCount = getChildCount();
        if (firstNode instanceof BSHType) {
            elementType = ((BSHType) firstNode).getType(callstack, interpreter);
            expression = getChild(1);
            statement = nodeCount > 2 ? getChild(2) : null;
        } else {
            expression = firstNode;
            statement = nodeCount > 1 ? getChild(1) : null;
        }
        final Object iteratee = expression.eval(callstack, interpreter);
        final CollectionManager cm = CollectionManager.getCollectionManager();
        final Iterator<?> iterator = cm.getBshIterator(iteratee);
        try {
            while ( !Thread.interrupted() && iterator.hasNext() ) {
                try {
                    NameSpace eachNameSpace = BlockNameSpace.getInstance(enclosingNameSpace, blockId);
                    callstack.swap(eachNameSpace);
                    Object value = iterator.next();
                    if ( value == null ) value = Primitive.NULL;
                    eachNameSpace.setTypedVariable(
                        varName, elementType, value, modifiers);
                } catch ( UtilEvalError e ) {
                    throw e.toEvalError(
                        "for loop iterator variable:"+ varName, this, callstack );
                }
                if (statement == null) continue; // not empty statement
                Object ret = statement instanceof BSHBlock
                    ? ((BSHBlock)statement).eval(callstack, interpreter, null)
                    : statement.eval(callstack, interpreter);
                if (ret instanceof ReturnControl) {
                    ReturnControl control = (ReturnControl)ret;

                    if (null != control.label)
                        if (null == label || !label.equals(control.label))
                            return ret;

                    if (control.kind == RETURN)
                        return ret;
                    else if (control.kind == BREAK)
                        break;
                    // if CONTINUE we just carry on
                }
            }
            return Primitive.VOID;
        } finally {
            callstack.swap(enclosingNameSpace);
        }
    }

    @Override
    public String toString() {
        return super.toString() + ": " + label + ": " + varName + ", final=" + isFinal;
    }
}
