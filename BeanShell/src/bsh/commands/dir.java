package bsh.commands;

import java.io.*;
import java.io.File; // leave this
import bsh.*;
import java.util.Date;
import java.util.Vector;

/*
	This is an example of a bsh command written in Java for speed.
*/

public class dir {

	static final String [] months = { "Jan", "Feb", "Mar", "Apr", 
		"May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec" };

	public static String usage() {
		return "usage: dir( String dir )\n       dir()";
	}

	/*
	static String getCWD( NameSpace namespace ) {
		try {
		return (String)namespace.resolveName("bsh.cwd");
		} catch ( EvalError e ) {
			Interpreter.debug("can't resolve cwd: "+e);
			return ".";
		}
	}
	*/

	public static void invoke( Interpreter env, NameSpace namespace ) {
		//String dir = getCWD( namespace );
		String dir = ".";
		invoke( env, namespace, dir );
	}

	public static void invoke( 
		Interpreter env, NameSpace namespace, String dir ) 
	{
		File file;
		try {
			file =  env.pathToFile( dir );
		} catch (IOException e ) {
			env.println("error reading path: "+e);
			return;
		}

		if ( !file.exists() || !file.canRead() ) {
			env.println( "Can't read " + file );
			return;
		}
		if ( !file.isDirectory() )  {
			env.println("'"+dir+"' is not a directory");
		}

		String [] files = file.list();
		files = bubbleSort(files);

		for( int i=0; i< files.length; i++ ) {
			File f = new File( dir + File.separator + files[i] );
			StringBuffer sb = new StringBuffer();
			sb.append( f.canRead() ? "r": "-" );
			sb.append( f.canWrite() ? "w": "-" );
			sb.append( "_" );
			sb.append( " ");

			Date d = new Date(f.lastModified());
			int day = d.getDate();
			sb.append( months[ d.getMonth() ] + " " + day );
			if ( day < 10 ) 
				sb.append(" ");

			sb.append(" ");

			// hack to get fixed length 'length' field
			int fieldlen = 8;
			StringBuffer len = new StringBuffer();
			for(int j=0; j<fieldlen; j++)
				len.append(" ");
			len.insert(0, f.length());
			len.setLength(fieldlen);
			// hack to move the spaces to the front
			int si = len.toString().indexOf(" ");
			if ( si != -1 ) {
				String pad = len.toString().substring(si);
				len.setLength(si);
				len.insert(0, pad);
			}
			
			sb.append( len );

			sb.append( " " + f.getName() );
			if ( f.isDirectory() ) 
				sb.append("/");

			env.println( sb.toString() );
		}
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

