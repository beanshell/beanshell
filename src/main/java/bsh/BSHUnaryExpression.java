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
import bsh.congo.parser.Node;
import bsh.congo.parser.Token.TokenType;
import static bsh.congo.parser.Token.TokenType.*;

public class BSHUnaryExpression extends BaseNode
{
    public boolean postfix = false;

    public TokenType getKind() {
        return getOperator() != null ? getOperator().getType() : null;
    }

    public Operator getOperator() {
        return firstChildOfType(Operator.class);
    }

    public Object eval( CallStack callstack, Interpreter interpreter)
        throws EvalError
    {
        Node node = getChild(0);

        // If this is a unary increment of decrement (either pre or postfix)
        // then we need an LHS to which to assign the result.  Otherwise
        // just do the unary operation for the value.
        try {
            if ( getKind() == INCR || getKind() == DECR ) {
                LHS lhs = ((BSHPrimaryExpression)node).toLHS(
                    callstack, interpreter );
                return lhsUnaryOperation( lhs, interpreter.getStrictJava() );
            } else
                return
                    unaryOperation( node.eval(callstack, interpreter), getOperator() );
        } catch ( UtilEvalError e ) {
            throw e.toEvalError( this, callstack );
        }
    }

    private Object lhsUnaryOperation( LHS lhs, boolean strictJava )
        throws UtilEvalError
    {
        Interpreter.debug("lhsUnaryOperation");
        Object prevalue, postvalue;
        prevalue = lhs.getValue();
        postvalue = unaryOperation(prevalue, getOperator());

        Object retVal;
        if ( postfix )
            retVal = prevalue;
        else
            retVal = postvalue;

        lhs.assign( postvalue, strictJava );
        return retVal;
    }

    private Object unaryOperation( Object op, Operator operator ) throws UtilEvalError
    {
        if ( op instanceof Boolean )
            op = (Boolean) op ? Primitive.TRUE : Primitive.FALSE;

        if ( !(op instanceof Primitive) )
            throw new UtilEvalError( "Unary operation " + operator
                + " inappropriate for object" );

        return Operators.unaryOperation((Primitive) op, operator.getType());
    }

    @Override
    public String toString() {
        return super.toString() + ": " + getOperator();
    }
}
