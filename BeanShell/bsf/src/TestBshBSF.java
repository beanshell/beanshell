
import com.ibm.bsf.*;
import java.util.Vector;

public class TestBshBSF 
{
	public static void main( String [] args ) 
		throws BSFException
	{
		BSFManager mgr = new BSFManager();

		// register beanshell with the BSF framework
		String [] extensions = { "bsh" };
		mgr.registerScriptingEngine( 
			"beanshell", "bsh.util.BeanShellBSFEngine", extensions );

		mgr.declareBean("foo", "fooString", String.class);
		mgr.declareBean("bar", "barString", String.class);
		mgr.registerBean("gee", "geeString");
		
		BSFEngine beanshellEngine = mgr.loadScriptingEngine("beanshell");

		String script = "foo + bar + bsf.lookupBean(\"gee\")";
		Object result = beanshellEngine.eval( "Test eval...", -1, -1, script );

		assert( result.equals("fooStringbarStringgeeString" ) );

		// test apply()
		Vector names = new Vector();
		names.addElement("name");
		Vector vals = new Vector();
		vals.addElement("Pat");

		script = "name + name";
		
		result = beanshellEngine.apply( 
			"source string...", -1, -1, script, names, vals );
	
		assert( result.equals("PatPat" ) );

		result = beanshellEngine.eval( "Test eval...", -1, -1, "name" );

		// name should not be set 
		assert( result == null );
	}

	static void assert( boolean cond ) {
		if ( cond )
			System.out.println("Passed...");
		else
			throw new Error("assert failed...");
	}
	
}

