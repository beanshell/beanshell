import bsh.*;

public class EvalReturnTest {
	public static void main( String [] args ) throws Exception {
		String text = "5;";
		Interpreter i = new Interpreter();
		System.out.println( i.eval( text ) );

		text = "return 5;";
		i = new Interpreter();
		System.out.println( i.eval( text ) );

		text = "if ( true ) return 5; else return 0";
		i = new Interpreter();
		System.out.println( i.eval( text ) );

		text = "if ( true ) return 5;;;";
		i = new Interpreter();
		System.out.println( i.eval( text ) );

		text = "if ( true ) 5";
		i = new Interpreter();
		System.out.println( i.eval( text ) );
		
	}
}
