
public class Fields {
	public static boolean staticField;

	public int x = 5;
	
	public static Fields getFields() {
		return new Fields();
	}

	public Fields getFields2() {
		return new Fields();
	}

	// ambiguity in field vs method
	public String ambigName = "field";
	public String ambigName() { return "method"; }
}
