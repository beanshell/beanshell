package bsh;

import java.util.*;

public class StringUtil {

	public static String [] split( String s, String delim) {
		Vector v = new Vector();
		StringTokenizer st = new StringTokenizer(s, delim);
		while ( st.hasMoreTokens() )
			v.addElement( st.nextToken() );
		String [] sa = new String [ v.size() ];
		v.copyInto( sa );
		return sa;
	}

	public static String [] bubbleSort( String [] in ) {
		Vector v = new Vector();
		for(int i=0; i<in.length; i++)
			v.addElement(in[i]);

		int n = v.size();
		boolean swap = true;
		while ( swap ) {
			swap = false;
			for(int i=0; i<(n-1); i++)
				if ( ((String)v.elementAt(i)).compareTo(
						((String)v.elementAt(i+1)) ) > 0 ) {
					String tmp = (String)v.elementAt(i+1);
					v.removeElementAt( i+1 );
					v.insertElementAt( tmp, i );
					swap = true;
				}
		}

		String [] out = new String [ n ];
		v.copyInto(out);
		return out;
	}

}
