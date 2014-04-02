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

class BSHThrowStatement extends SimpleNode
{
	BSHThrowStatement(int id) { super(id); }

	public Object eval( CallStack callstack, Interpreter interpreter)  
		throws EvalError
	{
		Object obj = ((SimpleNode)jjtGetChild(0)).eval(callstack, interpreter);

		// need to loosen this to any throwable... do we need to handle
		// that in interpreter somewhere?  check first...
		if(!(obj instanceof Exception))
			throw new EvalError("Expression in 'throw' must be Exception type",
				this, callstack );

		// wrap the exception in a TargetException to propagate it up
		throw new TargetError( (Exception)obj, this, callstack );
	}
}

