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
	variables declared or initialized in the forInit() section.  Elsewhere
	variable assignment acts like it is part of the containing block.

	This namespace takes as an argument a namespace comprising the forInit()
	variables and allows variables within it to shadow those in the parent
	namespace.  Otherwise all assignments are delegated to the parent 
	namespace.

	There may be more methods from NameSpace that should be overidden here.
*/
class ForBodyNameSpace extends NameSpace {
	NameSpace forInitNameSpace;

    public ForBodyNameSpace( NameSpace parent, NameSpace forInitNameSpace ) 
	{
		super( parent, parent.name + "ForBodyNameSpace" );
		this.forInitNameSpace = forInitNameSpace;
    }

	/**
		If the variables exists in the forInit space assign it there,
		otherwise in the parent space.
	*/
    public void	setVariable(String name, Object	o) throws EvalError {
		if ( forInitSpaceHasVariable( name ) ) 
			forInitNameSpace.setVariable( name, o );
		else
			getParent().setVariable( name, o );
    }

	/**
		If the variables exists in the forInit space assign it there,
		otherwise in the parent space.
		
		Note: this may not make sense...  if it exists we'll throw an
		error, right?  this can only happen in the forInit eval, right?
	*/
    public void	setTypedVariable( 
		String name, Class type, Object value,	boolean	isFinal ) 
		throws EvalError 
	{
		if ( forInitSpaceHasVariable( name ) ) 
			forInitNameSpace.setTypedVariable( name, type, value, isFinal );
		else
			getParent().setTypedVariable( name, type, value, isFinal );
    }

	boolean forInitSpaceHasVariable( String name ) {
		return ( forInitNameSpace != null 
				&& forInitNameSpace.getVariable(name, false) 
					!= Primitive.VOID );
	}

	/**
		get()s first check the forInit space.  If not defined there then
		they are delegated to the parent space.
	*/
    public Object getVariable( String name ) {
		Object o = null;

		if ( forInitNameSpace != null )
			o = forInitNameSpace.getVariable( name );

		if ( o == null || o == Primitive.VOID )
			o = getParent().getVariable( name );
		
		return o;
	}

}

