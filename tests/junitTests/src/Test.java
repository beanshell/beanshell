import org.junit.Assert;

public class Test {

	@org.junit.Test
	public void puzzler() {
		Assert.assertEquals(-1, check());
		Assert.assertEquals(-1, check(42));
		Assert.assertEquals(-1, check(1, 2));
		Assert.assertEquals(0, check(1, 1));
		Assert.assertEquals(0, check(2, 1, 1));
		Assert.assertEquals(1, check(1, 1, 2));
		Assert.assertEquals(-1, check(1, 1, 3));
		Assert.assertEquals(1, check(1, 1, 1, 1));
		Assert.assertEquals(1, check(1, 1, 0, 1, 1));
	}


	public int check(final int... array) {
		int a = 0;
		int b = array.length - 1;
		if (b <= 0) return -1;
		int sumA = 0;
		int sumB = 0;
		while (a < b) {
			if (sumA <= sumB) {
				sumA += array[a];
				a++;
			}
			if (sumA >= sumB) {
				sumB += array[b];
				b--;
			}
		}
		if (a == b) {
			// tie break
			if (sumA == sumB + array[b]) return a - 1;
			if (sumA + array[a] == sumB) return a;
		} else if (sumA == sumB) {
			return a - 1;
		}
		return -1;
	}
}
