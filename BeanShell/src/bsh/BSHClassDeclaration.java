/*****************************************************************************
 *                                                                           *
 *  This file is part of the BeanShell Java Scripting distribution.          *
 *  Documentation and updates may be found at http://www.beanshell.org/      *
 *                                                                           *
 *  Sun Public License Notice:                                               *
 *                                                                           *
 *  The contents of this file are subject to the Sun Public License Version  *
 *  1.0 (the "License"); you may not use this file except in compliance with *
 *  the License. A copy of the License is available at http://www.sun.com    * 
 *                                                                           *
 *  The Original Code is BeanShell. The Initial Developer of the Original    *
 *  Code is Pat Niemeyer. Portions created by Pat Niemeyer are Copyright     *
 *  (C) 2000.  All Rights Reserved.                                          *
 *                                                                           *
 *  GNU Public License Notice:                                               *
 *                                                                           *
 *  Alternatively, the contents of this file may be used under the terms of  *
 *  the GNU Lesser General Public License (the "LGPL"), in which case the    *
 *  provisions of LGPL are applicable instead of those above. If you wish to *
 *  allow use of your version of this file only under the  terms of the LGPL *
 *  and not to allow others to use your version of this file under the SPL,  *
 *  indicate your decision by deleting the provisions above and replace      *
 *  them with the notice and other provisions required by the LGPL.  If you  *
 *  do not delete the provisions above, a recipient may use your version of  *
 *  this file under either the SPL or the LGPL.                              *
 *                                                                           *
 *  Patrick Niemeyer (pat@pat.net)                                           *
 *  Author of Learning Java, O'Reilly & Associates                           *
 *  http://www.pat.net/~pat/                                                 *
 *                                                                           *
 *****************************************************************************/


package bsh;

/**
*/
class BSHClassDeclaration extends SimpleNode
{
	/**
		The class instance initializer method name.
		A BshMethod by this name is installed by the class delcaration into 
		the scripted class and it is called to initialize instances of the
		class.
	*/
	static final String CLASSINITNAME = "_bshClassInit";

	String name;
	Modifiers modifiers;
	int numInterfaces;
	Class [] interfaces;
	boolean extend;

	BSHClassDeclaration(int id) { super(id); }

	/**
	*/
	public Object eval( CallStack callstack, Interpreter interpreter )
		throws EvalError
	{
		int child = 0;
		NameSpace enclosingNameSpace = callstack.top();

		NameSpace superNameSpace;
		if ( extend ) 
		{
			BSHAmbiguousName superNode = (BSHAmbiguousName)jjtGetChild(child++);
			Object superClass = superNode.toObject( 
				callstack, interpreter, false /*forceclass*/ );

			if ( superClass instanceof ClassIdentifier )
				throw new EvalError(
					"BeanShell Scripted Classes cannot currently extend "
					+"Java types.", this, callstack );

			if ( ClassNameSpace.isScriptedClass( superClass ) )
				superNameSpace = ((This)superClass).getNameSpace();
			else
				throw new EvalError(
					"Class cannot extend type: "
						+superClass.getClass(), this, callstack );
		}
		else
			superNameSpace = enclosingNameSpace;

		// Get interfaces
		interfaces = new Class[numInterfaces];
		for( int i=0; i<numInterfaces; i++) {
			BSHAmbiguousName node = (BSHAmbiguousName)jjtGetChild(child++);
			interfaces[i] = node.toClass(callstack, interpreter);
			if ( !interfaces[i].isInterface() )
				throw new EvalError(
					"Type: "+node.text+" is not an interface!", 
					this, callstack );
		}

		BSHBlock block;
		if ( child < jjtGetNumChildren() )
			block = (BSHBlock)jjtGetChild(child);
		else
			block = new BSHBlock( ParserTreeConstants.JJTBLOCK );

		/*
			Install this class in the enclosingNameSpace.
			Make a scripted object representing the class and install it under
			the name of the class.
		*/

		NameSpace classStaticNameSpace = 
			new ClassNameSpace( superNameSpace, name, ClassNameSpace.CLASS );

		/*
			Evaluate the block in the classStaticNameSpace
			push it onto the stack and then call the block eval with the
			overrideNamespace option so that it uses ours.  Then pop it.
		*/
		callstack.push( classStaticNameSpace );
		// evaluate the static portion of the block in it
		block.eval( callstack, interpreter, true/*override*/ );

		// Add the block as a default constructor method

		BshMethod classInitializer =
			new BshMethod( 
				CLASSINITNAME, 
				null /*returnType*/,
				new String [0] /*argNames*/,
				new Class [0] /*argTypes*/,
				block,
				classStaticNameSpace/*declaringNameSpace*/,
				new Modifiers()
			);

		try {
			classStaticNameSpace.setMethod( CLASSINITNAME, classInitializer );	
		} catch ( UtilEvalError e ) {
			throw e.toEvalError(this, callstack);
		}
		
		callstack.pop();

		try {
			enclosingNameSpace.setVariable( 
				name, classStaticNameSpace.getThis( interpreter), false );
		} catch ( UtilEvalError e ) {
			throw e.toEvalError( this, callstack );
		}

		return Primitive.VOID;
	}

	public String toString() {
		return "ClassDeclaration: "+name;
	}
}
