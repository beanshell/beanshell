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

import java.util.Iterator;

/**
 * Implementation of the enhanced for(:) statement.
 *  This statement uses Iterator to support iteration over a wide variety
 *  of iterable types.
 *
 * @author Daniel Leuck
 * @author Pat Niemeyer
 */
class BSHEnhancedForStatement extends SimpleNode implements ParserConstants {

    final int blockId;
    String varName;
    boolean isFinal = false;


    BSHEnhancedForStatement(int id) {
        super(id);
        blockId = BlockNameSpace.blockCount.incrementAndGet();
    }


    public Object eval(CallStack callstack, Interpreter interpreter) throws EvalError {
        Modifiers modifiers = new Modifiers(Modifiers.PARAMETER);
        if (this.isFinal)
            modifiers.addModifier("final");
        Class elementType = null;
        Node expression;
        Node statement = null;
        NameSpace enclosingNameSpace = callstack.top();
        Node firstNode = jjtGetChild(0);
        int nodeCount = jjtGetNumChildren();
        if (firstNode instanceof BSHType) {
            elementType = ((BSHType) firstNode).getType(callstack, interpreter);
            if (elementType == null)
                elementType = Object.class;
            expression = jjtGetChild(1);
            if (nodeCount > 2) {
                statement = jjtGetChild(2);
            }
        } else {
            expression = firstNode;
            if (nodeCount > 1) {
                statement = jjtGetChild(1);
            }
        }
        final Object iteratee = expression.eval(callstack, interpreter);
        CollectionManager cm = CollectionManager.getCollectionManager();
        Iterator iterator = cm.getBshIterator(iteratee);
        Object returnControl = Primitive.VOID;
        while ( !Thread.interrupted() && iterator.hasNext() ) {
            try {
                NameSpace eachNameSpace = BlockNameSpace.getInstance(enclosingNameSpace, blockId);
                eachNameSpace.clear();
                callstack.swap(eachNameSpace);
                Object value = iterator.next();
                if ( value == null )
                    value = Primitive.NULL;
                eachNameSpace.setTypedVariable(
                    varName, elementType, value, modifiers);
            } catch ( UtilEvalError e ) {
                throw e.toEvalError(
                    "for loop iterator variable:"+ varName, this, callstack );
            }
            boolean breakout = false; // switch eats a multi-level break here?
            if (statement != null) {
                // not empty statement
                Object ret = statement.eval(callstack, interpreter);
                if (ret instanceof ReturnControl) {
                    switch (((ReturnControl) ret).kind) {
                        case RETURN:
                            returnControl = ret;
                            breakout = true;
                            break;
                        case CONTINUE:
                            break;
                        case BREAK:
                            breakout = true;
                            break;
                    }
                }
            }
            if (breakout) {
                break;
            }
        }
        callstack.swap(enclosingNameSpace);
        return returnControl;
    }

    @Override
    public String toString() {
        return super.toString() + ": " + varName + ", final=" + isFinal;
    }
}
