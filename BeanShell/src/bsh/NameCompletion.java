package bsh;
import java.util.*;

/**
	The interface for name completion.
	Also an inner utility class table
*/
public interface NameCompletion {

	public String [] completeName( String part );


	/**
		Maybe make some kind of NameCompletionResult object someday...

		This simple linear search could be reimplemented to be much more 
		efficient? (Anyone?)
	*/
	public static class Table extends ArrayList
	{
		/**
			Return an array containing a string element of the maximum 
			unambiguous namespace completion or, if there is no common prefix, 
			return the list of ambiguous names.
		*/
		public String [] completeName( String part ) {
			List found = new ArrayList();

			Iterator it = iterator();
			while( it.hasNext() ) {
				String name = (String)it.next();
				if ( name.startsWith( part ) )
					found.add( name );
			}

			if ( found.size() == 0 )
				return new String [0];

			// Find the max common prefix
			String maxCommon = (String)found.get(0);
			for(int i=1; i<found.size() && maxCommon.length() > 0; i++) {
				maxCommon = StringUtil.maxCommonPrefix( 
					maxCommon, (String)found.get(i) );

				// if maxCommon gets as small as part, stop trying
				if ( maxCommon.equals( part ) )
					break;
			}

			// Return max common or all ambiguous
			if ( maxCommon.length() > part.length() )
				return new String [] { maxCommon };
			else
				return (String[])(found.toArray(new String[0]));
		}
	}

}
