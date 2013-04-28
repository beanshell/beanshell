package bsh;

public class KnownIssue implements TestFilter {

    static final boolean SKIP_KOWN_ISSUES = System.getProperties().containsKey("skip_known_issues");


    public boolean skip() {
        return SKIP_KOWN_ISSUES;
    }

}
