package mypackage;

class Accessibility1 {

	private Accessibility1(int a, int b, int c) { }
	Accessibility1(int a, int b) { }
	protected Accessibility1(int a ) { }
	public Accessibility1() { }

	private int field1 = 1;
	int field2 = 2;
	protected int field3 = 3;
	public int field4 = 4;

	private int get1() { return 1; }
	private int get1(int a) { return 1; }
	int get2() { return 2; }
	protected int get3() { return 3; }
	public int get4() { return 4; }
}

