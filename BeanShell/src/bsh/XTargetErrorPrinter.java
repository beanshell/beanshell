package bsh;

import java.lang.reflect.UndeclaredThrowableException;

/**
	This exists to allow backwards compatability with jdk1.1, 1.2

	Note: This module relies on new features of JDK1.3 and will not compile
	with JDK1.2 or lower.  For those environments simply do not compile this
	class.

*/
class XTargetErrorPrinter implements TargetErrorPrinter 
{
	public String printTargetError( Throwable target ) {
		String re = target.toString();

		if ( target instanceof UndeclaredThrowableException )
			re += "\n" + 
			((UndeclaredThrowableException)target).getUndeclaredThrowable();

		return re;
	}
}
