
public class InnerClass {

    public static class Inner {
        public int x = 5;
        public static int y=6;

        public Inner() { }

        public static class Inner2 {
            public int z = 7;
            public Inner2() { }
        }
    }

    public class NonStaticInner {
        public int x = 5;

        public NonStaticInner() { }

        public class NonStaticInner2 {
            public int z = 7;
            public NonStaticInner2() { }
        }
    }

}
