package bsh;

// yah, I know
import java.util.*;

/**
	This simple linear search could be reimplemented to be much more 
	efficient? (Anyone?)
*/
public class NameCompletionTable extends ArrayList
{
	/**
		Return a List of Strings (up to maxAmbiguous in number)
	*/
	public String [] completeName( String part, int maxAmbiguous ) {
		List found = new ArrayList();

		Iterator it = iterator();
		while( it.hasNext() && found.size() < maxAmbiguous ) {
			String name = (String)it.next();
			if ( name.startsWith( part ) )
				found.add( name );
		}

		return (String[])(found.toArray(new String[0]));
	}
}
