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
	EvalError indicates that we cannot continue evaluating the script
	or the script has thrown an exception.

	EvalError may be thrown for a script syntax error, an evaluation 
	error such as referring to an undefined variable, an internal error.
	
	If the script has thrown an exception the exception will be wrapped
	in a TargetError.  
	@see TargetError
*/
public class EvalError extends Exception {
	/**
		Note: we could make this a vector and hold the full stack trace of
		method invocations that lead to the exception...
	*/
	SimpleNode node;

	// Note: no way to get at Throwable message, must maintain our own
	String message;

	public EvalError(String s) {
		setMessage(s);
	}

	public EvalError(String s, SimpleNode node) {
		this(s);
		this.node = node;
	}

	/**
	*/
	public String toString() {
		String trace;
		if ( node != null )
			trace = " : at Line: "+ node.getLineNumber() 
				+ " : in file: "+ node.getSourceFile()
				+ " : "+node.getText();
		else
			// users should not see this, in the worst case the interpreter
			// should insert the Line() AST node.
			trace = ": <at unknown location>";

			//return super.toString() + trace;
			return getMessage() + trace;
	}

	/**
		Re-throw the eval error, prepending msg to the message.
	*/
	public void reThrow( String msg ) 
		throws EvalError 
	{
		reThrow( msg, null );
	}

	/**
		Re-throw the eval error, specifying the node.
		If a node already exists the argument node is ignored.
		@see setNode()
	*/
	public void reThrow( SimpleNode node ) 
		throws EvalError 
	{
		reThrow( null, node );
	}

	/**
		Re-throw the eval error, prefixing msg to the message and specifying
		the node.  If a node already exists the addNode is ignored.
		@see setNode()
		<p>
		@param msg may be null for no additional message.
	*/
	public void reThrow( String addMsg, SimpleNode addNode ) 
		throws EvalError 
	{
		prependMessage( addMsg );
		addNode( addNode );
		throw this;
	}

	/**
		Set the AST node for trace info.
		@see reThrow
	
		This is useful for the interpreter if it detects that there is no
		trace info and wants to supply the Line() AST before printing.
	*/
	void setNode( SimpleNode node ) {
		this.node = node;
	}

	/**
		The error has trace info associated with it. 
		i.e. It has an AST node that can print its location and source text.
	*/
	SimpleNode getNode() {
		return node;
	}

	public String getErrorText() { 
		if ( node != null )
			return node.getText() ;
		else
			return "<unknown error>";
	}

	public int getErrorLineNumber() { 
		if ( node != null )
			return node.getLineNumber() ;
		else
			return -1;
	}

	public String getErrorSourceFile() {
		if ( node != null )
			return node.getSourceFile() ;
		else
			return "<unknown file>";
	}

	public String getMessage() { return message; }

	public void setMessage( String s ) { message = s; }

	/**
		Prepend the message if it is non-null.
	*/
	protected void prependMessage( String s ) { 
		if ( s != null )
			message = s + " : "+ message;
	}

	protected void addNode( SimpleNode addNode  ) {
		SimpleNode node = this.node;
		if ( node == null && addNode != null )
			node = addNode;
	}
}

