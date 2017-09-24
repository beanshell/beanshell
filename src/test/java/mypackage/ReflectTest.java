package mypackage;

/**
 * The Class ReflectTest.
 *
 * See if bsh can access the inner class
 */
public class ReflectTest {

    /**
     * Gets the runnable.
     *
     * @return the runnable
     */
    public Runnable getRunnable() {
        return new Runnable() {

            public void run() {
                System.out.println("run!");
            }
        };
    }
}
