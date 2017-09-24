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
 * Implementation of the enhanced for(:) statement.
 * This statement uses BshIterable to support iteration over a wide variety
 * of iterable types. Under JDK 1.1 this statement supports primitive and
 * Object arrays, Vectors, and enumerations. Under JDK 1.2 and later it
 * additionally supports collections.
 *
 * @author Daniel Leuck
 * @author Pat Niemeyer
 */
class BSHEnhancedForStatement extends SimpleNode implements ParserConstants {

    /** The Constant serialVersionUID. */
    private static final long serialVersionUID = 1L;

    /** The var name. */
    String varName;

    /**
     * Instantiates a new BSH enhanced for statement.
     *
     * @param id
     *            the id
     */
    BSHEnhancedForStatement(final int id) {
        super(id);
    }

    /** {@inheritDoc} */
    @Override
    public Object eval(final CallStack callstack, final Interpreter interpreter)
            throws EvalError {
        Class elementType = null;
        SimpleNode expression;
        SimpleNode statement = null;
        final NameSpace enclosingNameSpace = callstack.top();
        final SimpleNode firstNode = (SimpleNode) this.jjtGetChild(0);
        final int nodeCount = this.jjtGetNumChildren();
        if (firstNode instanceof BSHType) {
            elementType = ((BSHType) firstNode).getType(callstack, interpreter);
            expression = (SimpleNode) this.jjtGetChild(1);
            if (nodeCount > 2)
                statement = (SimpleNode) this.jjtGetChild(2);
        } else {
            expression = firstNode;
            if (nodeCount > 1)
                statement = (SimpleNode) this.jjtGetChild(1);
        }
        final BlockNameSpace eachNameSpace = new BlockNameSpace(
                enclosingNameSpace);
        callstack.swap(eachNameSpace);
        final Object iteratee = expression.eval(callstack, interpreter);
        if (iteratee == Primitive.NULL)
            throw new EvalError("The collection, array, map, iterator, or "
                    + "enumeration portion of a for statement cannot be null.",
                    this, callstack);
        final CollectionManager cm = CollectionManager.getCollectionManager();
        if (!cm.isBshIterable(iteratee))
            throw new EvalError(
                    "Can't iterate over type: " + iteratee.getClass(), this,
                    callstack);
        final BshIterator iterator = cm.getBshIterator(iteratee);
        Object returnControl = Primitive.VOID;
        while (iterator.hasNext()) {
            try {
                Object value = iterator.next();
                if (value == null)
                    value = Primitive.NULL;
                if (elementType != null)
                    eachNameSpace.setTypedVariable(this.varName/* name */,
                            elementType/* type */, value/* value */,
                            new Modifiers()/* none */);
                else
                    eachNameSpace.setVariable(this.varName, value, false);
            } catch (final UtilEvalError e) {
                throw e.toEvalError(
                        "for loop iterator variable:" + this.varName, this,
                        callstack);
            }
            boolean breakout = false; // switch eats a multi-level break here?
            if (statement != null) {
                // not empty statement
                final Object ret = statement.eval(callstack, interpreter);
                if (ret instanceof ReturnControl)
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
            if (breakout)
                break;
        }
        callstack.swap(enclosingNameSpace);
        return returnControl;
    }
}
