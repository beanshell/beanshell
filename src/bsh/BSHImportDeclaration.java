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

class BSHImportDeclaration extends SimpleNode
{
	public boolean importPackage;
	public boolean staticImport;
	public boolean superImport;

	BSHImportDeclaration(int id) { super(id); }

	public Object eval( CallStack callstack, Interpreter interpreter) 
		throws EvalError
	{
		NameSpace namespace = callstack.top();
		if ( superImport )
			try {
				namespace.doSuperImport();
			} catch ( UtilEvalError e ) {
				throw e.toEvalError( this, callstack  );
			}
		else 
		{
			if ( staticImport )
			{
				if ( importPackage )
				{
					Class clas = ((BSHAmbiguousName)jjtGetChild(0)).toClass( 
						callstack, interpreter );
					namespace.importStatic( clas );
				} else
					throw new EvalError( 
						"static field imports not supported yet", 
						this, callstack );
			} else 
			{
				String name = ((BSHAmbiguousName)jjtGetChild(0)).text;
				if ( importPackage )
					namespace.importPackage(name);
				else
					namespace.importClass(name);
			}
		}

        return Primitive.VOID;
	}
}

