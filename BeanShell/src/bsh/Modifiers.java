package bsh;

import java.util.Hashtable;

/**
	
	@author Pat Niemeyer (pat@pat.net)
*/
/*
	Note: which of these things should be checked at parse time vs. run time?
*/
class Modifiers implements java.io.Serializable
{
	public static final int METHOD=0, FIELD=1;
	Hashtable modifiers;

	/**
		@param context is METHOD or FIELD
	*/
	public void addModifier( int context, String name ) 
	{
		if ( modifiers == null )
			modifiers = new Hashtable();
		Object got = modifiers.put( name, this/*arbitrary flag*/ );
		if ( got != null )
			throw new IllegalStateException("Duplicate modifier: "+ name );

		int count = 0;
		if ( hasModifier("private") ) ++count;
		if ( hasModifier("protected") ) ++count;
		if ( hasModifier("public") ) ++count;
		if ( count > 1 )
			throw new IllegalStateException(
				"public/private/protected cannot be used in combination." );

		if ( context == METHOD )
			validateForMethod();
		else if ( context == FIELD )
			validateForField();
	}

	public boolean hasModifier( String name ) 
	{
		return modifiers.get(name) != null;
	}

	// could refactor these a bit
	private void validateForMethod() 
	{ 
		if ( hasModifier("volatile") )
			throw new IllegalStateException(
				"Method cannot be declared 'volatile'");
		if ( hasModifier("transient") )
			throw new IllegalStateException(
				"Method cannot be declared 'transient'");
	}
	private void validateForField() 
	{ 
		if ( hasModifier("synchronized") )
			throw new IllegalStateException(
				"Variable cannot be declared 'synchronized'");
		if ( hasModifier("native") )
			throw new IllegalStateException(
				"Method cannot be declared 'native'");
		if ( hasModifier("abstract") )
			throw new IllegalStateException(
				"Method cannot be declared 'abstract'");
	}
}
