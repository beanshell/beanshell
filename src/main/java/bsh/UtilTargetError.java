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
    UtilTargetError is an error corresponding to a TargetError but thrown by a
    utility or other class that does not have the caller context (Node)
    available to it.  See UtilEvalError for an explanation of the difference
    between UtilEvalError and EvalError.
    <p>

    @see UtilEvalError
*/
public class UtilTargetError extends UtilEvalError
{
    public Throwable t;

    public UtilTargetError(String message, Throwable t) {
        super(message);
        this.t = t;
    }

    public UtilTargetError(Throwable t) {
        this(null, t);
    }

    /**
        Override toEvalError to throw TargetError type.
    */
    public EvalError toEvalError(
        String msg, SimpleNode node, CallStack callstack )
    {
        if (msg == null)
            msg = getMessage();
        else
            msg = msg + ": " + getMessage();

        return new TargetError(msg, t, node, callstack, false);
    }
}

