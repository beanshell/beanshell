package mypackage;

public class Accessibility2 extends Accessibility1 
{
	private Accessibility2(int a, int b, int c) { }
	Accessibility2(int a, int b) { }
	protected Accessibility2(int a ) { }
	public Accessibility2() { }

	private int field1 = 1;
	int field2 = 2;
	protected int field3 = 3;
	public int field4 = 4;

	private int getB1() { return 1; }
	int getB2() { return 2; }
	protected int getB3() { return 3; }
	public int getB4() { return 4; }
}

