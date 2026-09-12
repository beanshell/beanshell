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

class BSHArguments extends SimpleNode
{
    BSHArguments(int id) { super(id); }

    /**
        This node holds a set of arguments for a method invocation or
        constructor call.

        Note: arguments are not currently allowed to be VOID.
    */
    /*
        Disallowing VOIDs here was an easy way to support the throwing of a
        more descriptive error message on use of an undefined argument to a
        method call (very common).  If it ever turns out that we need to
        support that for some reason we'll have to re-evaluate how we get
        "meta-information" about the arguments in the various invoke() methods
        that take Object [].  We could either pass BSHArguments down to
        overloaded forms of the methods or throw an exception subtype
        including the argument position back up, where the error message would
        be compounded.
    */
    public Object[] getArguments( CallStack callstack, Interpreter interpreter)
        throws EvalError
    {
        return getCallArguments(callstack, interpreter).values;
    }

    CallArguments getCallArguments(CallStack callstack, Interpreter interpreter)
            throws EvalError {
        // Evaluate each child once, keeping null's declared type beside its value.
        Class<?>[] types = new Class<?>[jjtGetNumChildren()];
        Object[] args = new Object[jjtGetNumChildren()];
        for(int i = 0; i < args.length; i++)
        {
            CallArguments.Result result = new CallArguments.Result();
            args[i] = CallArguments.eval(jjtGetChild(i), callstack, interpreter, result);
            types[i] = args[i] == Primitive.NULL || args[i] == null
                    ? result.type : Types.getType(args[i]);
            if ( args[i] == Primitive.VOID )
                throw new EvalException( "Undefined argument: " +
                    jjtGetChild(i).getText(), this, callstack );
        }

        return new CallArguments(args, types);
    }
}

