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
