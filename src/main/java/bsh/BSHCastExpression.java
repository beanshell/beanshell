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
    Implement casts.

    I think it should be possible to simplify some of the code here by
    using the Types.getAssignableForm() method, but I haven't looked
    into it.
*/
class BSHCastExpression extends SimpleNode {

    public BSHCastExpression(int id) { super(id); }

    /**
        @return the result of the cast.
    */
    public Object eval(
        CallStack callstack, Interpreter interpreter ) throws EvalError
    {
        Class toType = ((BSHType)jjtGetChild(0)).getType(
            callstack, interpreter );
        Node expression = jjtGetChild(1);

        // evaluate the expression
        Object fromValue = expression.eval(callstack, interpreter);

        // TODO: need to add isJavaCastable() test for strictJava
        // (as opposed to isJavaAssignable())
        try {
            return Types.castObject( fromValue, toType, Types.CAST );
        } catch ( UtilEvalError e ) {
            throw e.toEvalError( this, callstack  );
        } catch ( InterpreterError e ) {
            // An explicit narrowing cast truncates an out-of-range numeric
            // value per JLS 5.1.3, unlike an implicit typed declaration
            // (which is only forgiven in strict-Java mode). Only reached
            // when the normal cast path above already rejected the value;
            // in-range casts (including bsh's unsigned-byte-widening cast
            // to int) never reach here.
            if ( fromValue instanceof Primitive && ((Primitive) fromValue).isNumber()
                    && Types.isNumeric(toType) )
                return Primitive.wrap( Primitive.castNumberStrictJava(
                    toType, ((Primitive) fromValue).numberValue() ), toType );
            throw e;
        }
    }

}
