package fixture;

public class OptionalMethodFixture {
    public MissingDependency unusedMethod() {
        return null;
    }

    public static class MissingDependency {}
}
