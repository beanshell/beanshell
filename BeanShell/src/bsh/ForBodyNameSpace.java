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


package	bsh;

/**
    A specialized namespace	for the body of a "for" statement.
	The for statement body acts like a child namespace but only for 
	variables declared in the forInit() section.  Elsewhere variable 
	assignment acts like it is part of the containing block.  

	As of 1.1a9 untyped vars in the for-init are placed in the parent namespace.
	See JAVA_STYLE_FOR_LOOPS below.

	This namespace takes as an argument a namespace comprising the forInit()
	variables and allows variables within it to shadow those in the parent
	namespace.  Otherwise all assignments are delegated to the parent 
	namespace.
*/
class ForBodyNameSpace extends NameSpace 
	{
	NameSpace forInitNameSpace;

	/**
		See the docs regarding for-loops and java compatability.
		If we are doing java style for loops allow refs to untyped vars in
		the for-init section to refer to parent scope instead of local for-init
		scope. This emulates Java but is inconsistent with the bsh model that
		all var allocation is local unless scoped.
	*/
	protected static final boolean JAVA_STYLE_FOR_LOOPS = true;

    public ForBodyNameSpace( NameSpace parent, NameSpace forInitNameSpace ) 
		throws EvalError
	{
		super( parent, parent.name + "/ForBodyNameSpace" );
		this.forInitNameSpace = forInitNameSpace;

		/*
			Ugly hack as part of support for Java style for loops.
			Move untyped vars that were initialized in the for-init to
			the parent namespace. 
		*/
		if ( JAVA_STYLE_FOR_LOOPS ) 
			copyUntypedVars( forInitNameSpace, parent );
    }

	/**
		Override the standard namespace behavior.
		If the variables exists in the forInit space assign it there,
		otherwise in the parent space.
	*/
    public void	setVariable(String name, Object	o) throws EvalError {
		if ( forInitHasVar( name ) ) 
			forInitNameSpace.setVariable( name, o );
		else
			getParent().setVariable( name, o );
    }

	/**
		Override the standard namespace behavior.
		If the variables exists in the forInit space assign it there,
		otherwise in the parent space.
		
		Note: this may not make sense...  if it exists we'll throw an
		error, right?  this can only happen in the forInit eval, right?
	*/
    public void	setTypedVariable( 
		String name, Class type, Object value,	boolean	isFinal ) 
		throws EvalError 
	{
		if ( forInitHasVar( name ) ) 
			forInitNameSpace.setTypedVariable( name, type, value, isFinal );
		else
			getParent().setTypedVariable( name, type, value, isFinal );
    }

	/**
		Override the standard namespace behavior.  First check the forInit 
		space.  If the variable is not defined there then delegate to our 
		parent space.
	*/
    public Object getVariableImpl( String name, boolean recurse ) {
		Object var = getForInitVar( name );
		if ( var == null )  // not in for-init scope
			var = getParent().getVariableImpl( name, recurse );
		return var;
	}

	boolean forInitHasVar( String name ) {
		return getForInitVar( name ) != null;
	}

	/**
		@return raw value - Typed, plain or null
	*/
	Object getForInitVar( String name ) 
	{
		Object var = null;

		if ( forInitNameSpace != null )
			var = forInitNameSpace.getVariableImpl( name, false );
	
		// If we are doing java style for loops allow refs to untyped vars
		// to pass to parent scope instead of for-init.
		if ( JAVA_STYLE_FOR_LOOPS && !(var instanceof TypedVariable) )
			var = null;

		return var;
	}

	/**
		copy utyped variables in namespace 'from' to namespace 'to'
		Part of the hack to support java style for loops.
	*/
	private void copyUntypedVars( NameSpace from, NameSpace to ) 
		throws EvalError
	{
		if ( from == null )
			return;
		String [] vars = from.getVariableNames();
		for(int i=0; i<vars.length; i++) {
			Object value = from.getVariableImpl( vars[i], false );
			if ( !(value instanceof TypedVariable) )
				try {
					to.setVariable( vars[i], value );
				} catch ( EvalError e ) {
					e.reThrow("Error in for-init var assignment: "+vars[i]);
				}
		}
	}
}

