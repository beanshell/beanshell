/*****************************************************************************
 *                                                                           *
 *  This file is part of the BeanShell Java Scripting distribution.          *
 *  Documentation and updates may be found at http://www.beanshell.org/      *
 *                                                                           *
 *  BeanShell is distributed under the terms of the LGPL:                    *
 *  GNU Library Public License http://www.gnu.org/copyleft/lgpl.html         *
 *                                                                           *
 *  Patrick Niemeyer (pat@pat.net)                                           *
 *  Author of Exploring Java, O'Reilly & Associates                          *
 *  http://www.pat.net/~pat/                                                 *
 *                                                                           *
 *****************************************************************************/


package bsh;

class BSHImportDeclaration extends SimpleNode
{
	public boolean importPackage;
	public boolean superImport;

	BSHImportDeclaration(int id) { super(id); }

	public Object eval( NameSpace namespace, Interpreter interpreter) 
		throws EvalError
	{
		if ( superImport )
			NameSpace.doSuperImport( interpreter );
		else {
			String name = 
				((BSHAmbiguousName)jjtGetChild(0)).getName(namespace).value;

			if ( importPackage )
				namespace.importPackage(name);
			else
				namespace.importClass(name);
		}

        return Primitive.VOID;
	}
}

