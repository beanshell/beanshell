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

class BSHLHSPrimaryExpression extends SimpleNode
{
	BSHLHSPrimaryExpression(int id) { super(id); }

	public LHS toLHS(CallStack callstack, Interpreter interpreter) 
		throws EvalError
	{
		/*
			Get the prefix, which may be either an ambiguous name or a
			method invocation.  

			The method invocation bit here is somewhat of a hack to handle 
			the fact that we have moved prefix method invocation into the 
			PrimaryPrefix in order to get it to always produce an object type.
			This is too complicated.
		*/
		int childNum = 0;
		SimpleNode prefixNode = (SimpleNode)jjtGetChild(childNum++);
		Object prefixValue = null;
		LHS lhs = null;
		if ( prefixNode instanceof BSHAmbiguousName ) 
			lhs = ((BSHAmbiguousName)prefixNode).toLHS( callstack, interpreter);
		else
			// Currently the only case is for BSHMethodInvocation
			prefixValue = 
				((SimpleNode)prefixNode).eval( callstack, interpreter);

		// If the prefix is an object and not an LHS it requires at least
		// one suffix to make an LHS
		// Currently the only case is for BSHMethodInvocation
		if ( prefixValue != null )
			lhs = ((BSHLHSPrimarySuffix)jjtGetChild(childNum++)).doLHSSuffix(
				prefixValue, callstack, interpreter);

		// Apply the suffixes
		int numChildren = jjtGetNumChildren(); 
		while( childNum<numChildren )
			lhs = ((BSHLHSPrimarySuffix)jjtGetChild(childNum++)).doLHSSuffix(
				lhs.getValue(), callstack, interpreter);

		return lhs;
	}
}

