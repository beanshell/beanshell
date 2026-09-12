import java.util.ArrayList;

public class MethodSelection {

    public Class constructedWith;
    public String which;
    public String[] varargs;

    // constructors

    public MethodSelection( Object o ) {
        constructedWith = o.getClass();
//        System.out.println("selected object constr");
    }
    public MethodSelection( String o ) {
        constructedWith = o.getClass();
        which = "one";
//        System.out.println("selected string constr");
    }
    public MethodSelection( long o ) {
        constructedWith = Long.TYPE;
//        System.out.println("selected long constr");
    }
    public MethodSelection( int o ) {
        constructedWith = Integer.TYPE;
//        System.out.println("selected int constr");
    }
    public MethodSelection( byte o ) {
        constructedWith = Byte.TYPE;
//        System.out.println("selected byte constr");
    }
    public MethodSelection( short o ) {
        constructedWith = Short.TYPE;
//        System.out.println("selected short constr");
    }
    public MethodSelection() {
        constructedWith = Void.TYPE;
//        System.out.println("no args constr");
    }
    public MethodSelection(String arg1, String arg2) {
        constructedWith = (new String[0]).getClass();
        which = "two";
//        System.out.println("two String argument constr");
    }
    public MethodSelection(String... args) {
        constructedWith = (new String[0]).getClass();
        which = "three";
//        System.out.println("var args constr");
    }


    // static method selection

    public static Class get_static( Object o ) {
//        System.out.println("selected object method");
        return o.getClass();
    }
    public static Class get_static( String o ) {
//        System.out.println("selected string method");
        return o.getClass();
    }
    public static Class get_static( int o ) {
//        System.out.println("selected int method");
        return Integer.TYPE;
    }
    public static Class get_static( long o ) {
//        System.out.println("selected long method");
        return Long.TYPE;
    }
    public static Class get_static( byte o ) {
//        System.out.println("selected byte method");
        return Byte.TYPE;
    }
    public static Class get_static( short o ) {
//        System.out.println("selected short method");
        return Short.TYPE;
    }
    public static Class get_static() {
//        System.out.println("selected no args method");
        return Void.TYPE;
    }

    // dynamic method selection

    public Class get_dynamic( Object o ) {
//        System.out.println("selected object method");
        return o.getClass();
    }
    public Class get_dynamic( String o ) {
//        System.out.println("selected string method");
        return o.getClass();
    }
    public Class get_dynamic( int o ) {
//        System.out.println("selected int method");
        return Integer.TYPE;
    }
    public Class get_dynamic( long o ) {
//        System.out.println("selected long method");
        return Long.TYPE;
    }
    public Class get_dynamic( byte o ) {
//        System.out.println("selected byte method");
        return Byte.TYPE;
    }
    public Class get_dynamic( short o ) {
//        System.out.println("selected short method");
        return Short.TYPE;
    }
    public Class get_dynamic() {
//        System.out.println("selected no args method");
        return Void.TYPE;
    }

    // for testing most specific method selection
    public String method1(String str) {
        return "one";
    }
    public String method1(String str, String[] strs) {
        return "two";
    }
    public String method1(StringBuilder sb, String string, ArrayList list,
                        int[] ints) {
        return "three";
    }
    public String method1(StringBuilder sb, String string, ArrayList list,
                        int[] ints, int int2, String last) {
        return "four";
    }
    public String method1(StringBuilder sb, String string, String[] strs,
                        int[] ints, int int2, String last) {
        return "five";
    }

    // for testing VarArgs and most specific method selection
    public String method2(String str) {
        // most specific for method2("Hello")
        varargs = new String[] {str};
        return "one";
    }
    public String method2(String str, String str1) {
        // most specific for method2("Hello", "World")
        varargs = new String[] {str, str1};
        return "two";
    }
    public String method2(String str, String... str2) {
        // most specific for method2("Hello", "World", "This", "is", "me")
        varargs = str2;
        return "three";
    }

    /*
        If we try to invoke an instance method through a static context
        javac will error... rather than take the widening match.
        See methodselection2.bsh
    */
    public static Class staticVsDynamic1( Object obj ) {
//        System.out.println("Object");
        return Object.class;
    }
    public Class staticVsDynamic1( String obj ) {
//        System.out.println("String");
        return String.class;
    }

    public static void main( String [] args ) {
//        System.out.println("should be string");
        new MethodSelection().staticVsDynamic1( "foo" );

//        System.out.println("should be object");
        new MethodSelection().staticVsDynamic1( new Object() );

    }

    private String foo( Integer x ) { return "private"; }
    public String foo( String x ) { return "public"; }

}
