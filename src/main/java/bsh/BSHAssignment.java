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

import bsh.congo.tree.BaseNode;
import bsh.congo.tree.Operator;
import bsh.legacy.ParserConstants;
import bsh.congo.tree.Operator;
import bsh.congo.parser.Token.TokenType;
import static bsh.congo.parser.Token.TokenType.*;

public class BSHAssignment extends BaseNode {
    private static final long serialVersionUID = 1L;
    private TokenType tokenType;
    public TokenType getKind() {
        if (null == tokenType)
            tokenType = getOperator() != null ? getOperator().getType() : null;
        return tokenType;
    }

    public void setKind(TokenType kind) {
        tokenType = kind;
    }

    public Operator getOperator() {
        return firstChildOfType(Operator.class);
    }


    public Object eval(CallStack callstack, Interpreter interpreter)
            throws EvalError {
        if ( getOperator() == null ) try {
            return getChild(0).eval(callstack, interpreter);
        } catch (SafeNavigate aborted) {
            return Primitive.NULL;
        }

        BSHPrimaryExpression lhsNode = firstChildOfType(BSHPrimaryExpression.class);

        boolean strictJava = interpreter.getStrictJava();
        LHS lhs = lhsNode.toLHS( callstack, interpreter);

        // For operator-assign operations save the lhs value before evaluating
        // the rhs.  This is correct Java behavior for postfix operations
        // e.g. i=1; i+=i++; // should be 2 not 3
        Object lhsValue = null;
        if ( getKind() != ASSIGN ) try { // assign doesn't need the lhs value
            lhsValue = lhs.getValue();
        } catch ( UtilEvalError e ) {
            throw e.toEvalError( this, callstack );
        }

        if ( getKind() == NULLCOALESCEASSIGN &&  Primitive.NULL != lhsValue )
            return lhsValue; // return non null lhs before evaluating rhs

        // evaluate the right hand side
        Object rhs = getChild(1).eval(callstack, interpreter);

        if ( rhs == Primitive.VOID )
            throw new EvalError("illegal void assignment", this, callstack);

        try {
            switch( getKind() ) {
                case ASSIGN:
                    if (lhs.isFinal()) {
                        lhs.getVariable().setValue(rhs, Variable.ASSIGNMENT);
                        return rhs;
                    }
                    return lhs.assign(rhs, strictJava);

                case NULLCOALESCEASSIGN:
                    // we already know lhs is null
                    return lhs.assign(rhs, strictJava);

                case PLUSASSIGN:
                    if ( Primitive.NULL == lhsValue && lhs.getType() == String.class )
                        lhsValue = "null";
                    return lhs.assign(
                        operation(lhsValue, rhs, PLUS), strictJava);

                case MINUSASSIGN:
                    return lhs.assign(
                        operation(lhsValue, rhs, MINUS), strictJava);

                case STARASSIGN:
                    return lhs.assign(
                        operation(lhsValue, rhs, STAR), strictJava);

                case SLASHASSIGN:
                    return lhs.assign(
                        operation(lhsValue, rhs, SLASH), strictJava);

                case ANDASSIGN:
                    return lhs.assign(
                        operation(lhsValue, rhs, BIT_AND), strictJava);

                case ORASSIGN:
                    return lhs.assign(
                        operation(lhsValue, rhs, BIT_OR), strictJava);

                case XORASSIGN:
                    return lhs.assign(
                        operation(lhsValue, rhs, XOR), strictJava);

                case MODASSIGN:
                    return lhs.assign(
                        operation(lhsValue, rhs, MOD), strictJava );

                case POWERASSIGN:
                    return lhs.assign(
                            operation(lhsValue, rhs, POWER), strictJava);

                case LSHIFTASSIGN:
                    return lhs.assign(
                        operation(lhsValue, rhs, LSHIFT), strictJava);

                case RSIGNEDSHIFTASSIGN:
                    return lhs.assign(
                    operation(lhsValue, rhs, RSIGNEDSHIFT ), strictJava);

                case RUNSIGNEDSHIFTASSIGN:
                    return lhs.assign(
                        operation(lhsValue, rhs, RUNSIGNEDSHIFT),
                        strictJava);

                default:
                    throw new InterpreterError(
                        "unimplemented operator in assignment BSH");
            }
        } catch ( UtilEvalError e ) {
            throw e.toEvalError( this, callstack );
        }
    }

    /** Convenience method to shuffle the different types off to
     * the correct helper classes for application.
     * @param lhs the assignee
     * @param rhs the assigner
     * @param kind the operation kind
     * @return the operated result
     * @throws UtilEvalError of invalidation */
    private Object operation( Object lhs, Object rhs, TokenType kind )
            throws UtilEvalError {
        if ( lhs instanceof String || lhs.getClass().isArray() )
            return Operators.arbitraryObjectsBinaryOperation(lhs, rhs, kind);

        if ( rhs == Primitive.NULL )
            throw new UtilEvalError(
                "Illegal use of null object or 'null' literal" );

        if ( (lhs instanceof Boolean || lhs instanceof Character
               || lhs instanceof Number || lhs instanceof Primitive)
               && (rhs instanceof Boolean || rhs instanceof Character
               || rhs instanceof Number || rhs instanceof Primitive) ) {
            return Operators.binaryOperation(lhs, rhs, kind);
        }

        throw new UtilEvalError("Non primitive value in operator: " +
            lhs.getClass() + " " + kind + " " + rhs.getClass());
    }

    @Override
    public String toString() {
        return super.toString() + (null == getOperator() ? "" : ": " + getOperator());
    }
}
