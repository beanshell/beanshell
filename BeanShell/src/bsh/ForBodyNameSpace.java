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

