package bsh;

/**
	The map of extended features supported by the runtime in which we live.
*/
public class Capabilities {
	static boolean checkedForSwing, haveSwing;
	public static boolean haveSwing() {

		if ( checkedForSwing )
			return haveSwing;

		haveSwing = BshClassManager.classExists( "javax.swing.JButton" );
		checkedForSwing = true;
		return ( haveSwing );
	}

	static boolean checkedForProxyMech, haveProxyMech;
	public static boolean haveProxyMechanism() {
		if ( checkedForProxyMech )
			return haveProxyMech;

		haveProxyMech = BshClassManager.classExists( 
			"java.lang.reflect.Proxy" );
		checkedForProxyMech = true;
		return haveProxyMech;
	}

}


