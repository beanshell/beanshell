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

import java.util.Vector;

class BSHTryStatement extends SimpleNode
{
	BSHTryStatement(int id)
	{
		super(id);
	}

	public Object eval(NameSpace namespace, Interpreter interpreter)  throws EvalError
	{
		BSHBlock tryBlock = ((BSHBlock)jjtGetChild(0));

		Vector catchParams = new Vector();
		Vector catchBlocks = new Vector();

		int nchild = jjtGetNumChildren();
		Node node = null;
		int i=1;
		while((i < nchild) && ((node = jjtGetChild(i++)) instanceof BSHFormalParameter))
		{
			catchParams.addElement(node);
			catchBlocks.addElement(jjtGetChild(i++));
			node = null;
		}
		// finaly block
		BSHBlock finallyBlock = null;
		if(node != null)
			finallyBlock = (BSHBlock)node;

// Why both of these?

		TargetError target = null;
		Throwable thrown = null;
		Object ret = null;

		// Evaluate the contents of the try { } block 
		try {
			ret = tryBlock.eval(namespace, interpreter);
		}
		catch(TargetError e) {
			target = e;
		}

		if ( target != null )
			thrown = target.getTarget();

		if(thrown != null)	// We have an exception, find a catch
		{
			int n = catchParams.size();
			for(i=0; i<n; i++)
			{
				// get catch block
				BSHFormalParameter fp = 
					(BSHFormalParameter)catchParams.elementAt(i);

				/* 
					Does the formal parameter need to evaluated every
				  	time?  I guess if it needs to reflect changes in the 
				  	type namespace...
				*/
				fp.eval(namespace, interpreter);

				// If the param is typed check assignability
				if ( fp.type != null ) 
					try {
						thrown = (Throwable)NameSpace.checkAssignableFrom(
							thrown, fp.type);
					} catch(EvalError e) {
						/* Catch the mismatch and continue to try the next
							Note: this is innefficient, should have an 
							isAssignableFrom() that doesn't throw */
						continue;
					}

				// Found match, execute catch block
				BSHBlock cb = (BSHBlock)(catchBlocks.elementAt(i));

				if ( fp.type == BSHFormalParameter.UNTYPED )
					namespace.setVariable(fp.name, thrown );
				else
					namespace.setTypedVariable(fp.name, fp.type, thrown,false);

				ret = cb.eval( namespace, interpreter );
				target = null;  // handled target
				break;
			}
		}

		// evaluate finally block
		if(finallyBlock != null)
			ret = finallyBlock.eval(namespace, interpreter);

		// exception fell through, throw it upward...
		if(target != null)
			throw target;

		if(ret instanceof ReturnControl)
			return ret;
		else	
			return Primitive.VOID;
	}
}
