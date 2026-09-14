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
 *                                                                           *
 *****************************************************************************/

package bsh;

/**
    Wraps an EvalError as an unchecked exception, for script errors raised
    from a context that cannot declare a checked exception -- e.g. a
    generated wrapper class implementing a Java functional interface whose
    single abstract method declares no such throws clause.
    @see EvalError
*/
public class RuntimeEvalError extends RuntimeException {

    private final EvalError error;

    RuntimeEvalError(String s, Node node, CallStack callstack) {
        this.error = new EvalError(s, node, callstack);
    }

    RuntimeEvalError(String s, Node node, CallStack callstack, Throwable cause) {
        this.error = new EvalError(s, node, callstack);
        initCause(cause);
    }

    RuntimeEvalError(EvalError error) {
        this.error = error;
        initCause(error);
    }

    /** The script error, with its line, file and script stack. */
    public EvalError getEvalError() {
        return error;
    }

    @Override
    public String getMessage() {
        return error.getMessage();
    }
}
