package bsh.collection;

import java.util.Iterator;
import java.util.Collection;
import java.util.Enumeration;
//import java.util.Map;

import java.lang.reflect.Array;

/**
 *	This is the implementation of:
 *	BshIterator - a dynamically loaded extension that supports the collections
 *	API supported by JDK1.2 and greater.
 *
 *	@author Daniel Leuck
 *	@author Pat Niemeyer
 */
public class CollectionIterator implements bsh.BshIterator
{
	private Iterator iterator;

	/**
	 * Construct a basic CollectionIterator
	 *
	 * @param The object over which we are iterating
	 *
	 * @throws java.lang.IllegalArgumentException If the argument is not a
	 * supported (i.e. iterable) type.
	 *
	 * @throws java.lang.NullPointerException If the argument is null
	 */	
	public CollectionIterator(Object iterateOverMe) {
		iterator = createIterator(iterateOverMe);	
	}

	/**
	 * Create an iterator over the given object
	 *
	 * @param iterateOverMe Object of type Iterator, Collection, or types
	 * supported by CollectionManager.BasicBshIterator
	 *
	 * @return an Iterator
	 *
	 * @throws java.lang.IllegalArgumentException If the argument is not a
	 * supported (i.e. iterable) type.
	 *
	 * @throws java.lang.NullPointerException If the argument is null
	 */
	protected Iterator createIterator(Object iterateOverMe) 
	{
		if (iterateOverMe==null)
			throw new NullPointerException("Object arguments passed to " +
				"the CollectionIterator constructor cannot be null.");

		if (iterateOverMe instanceof Iterator)
			return (Iterator)iterateOverMe;

		if (iterateOverMe instanceof Collection) 
			return ((Collection)iterateOverMe).iterator();

		/*
		Should we be able to iterate over maps?
		if (iterateOverMe instanceof Map)
			return ((Map)iterateOverMe).entrySet().iterator();
		*/

		throw new IllegalArgumentException(
			"Cannot enumerate object of type "+iterateOverMe.getClass());
	}

	/**
	 * Fetch the next object in the iteration
	 *
	 * @return The next object
	 */	
	public Object next() {
		return iterator.next();
	}
	
	/**
	 * Returns true if and only if there are more objects available
	 * via the <code>next()</code> method
	 *
	 * @return The next object
	 */	
	public boolean hasNext() {
		return iterator.hasNext();	
	}
}
