package bsh;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** SimpleNode.getSourceText() on unusual token streams. */
public class SimpleNodeSourceTextTest {

    private static Token token(String image, int line, int beginColumn, int endColumn) {
        Token t = new Token();
        t.image = image;
        t.beginLine = line;
        t.endLine = line;
        t.beginColumn = beginColumn;
        t.endColumn = endColumn;
        return t;
    }

    private static SimpleNode node(Token first, Token last) {
        SimpleNode node = new SimpleNode(0);
        node.firstToken = first;
        node.lastToken = last;
        return node;
    }

    @Test
    public void nodeWithoutTokens_fallsBackToGetText() {
        SimpleNode none = new SimpleNode(0);
        assertEquals(none.getText(), none.getSourceText());
        assertEquals("", none.getSourceText());

        Token only = token("x", 1, 1, 1);
        assertEquals(node(only, null).getText(), node(only, null).getSourceText());
        assertEquals(node(null, only).getText(), node(null, only).getSourceText());
    }

    @Test
    public void tokenChainThatNeverReachesTheLastToken_endsWithTheChain() {
        Token a = token("a", 1, 1, 1);
        Token b = token("b", 1, 3, 3);
        a.next = b;
        Token elsewhere = token("c", 1, 5, 5);
        assertEquals("a b", node(a, elsewhere).getSourceText());
    }

    @Test
    public void lineCommentEndedByCarriageReturn_isKeptWhole() throws Exception {
        String text = "foo() { // note\r return 1; }";
        assertEquals(text, ScriptedSource.parseMethod(text).sourceText());
    }

    @Test
    public void lineCommentEndedByLineFeed_isKeptWhole() throws Exception {
        String text = "foo() { // note\n return 1; }";
        assertEquals(text, ScriptedSource.parseMethod(text).sourceText());
    }

    @Test
    public void linesLeftOfTheFirstToken_startAtTheLeftEdge() throws Exception {
        assertEquals("foo(a) {\nreturn a;\n}",
            ScriptedSource.parseMethod("      foo(a) {\n  return a;\n}").sourceText());
    }

    @Test
    public void overlappingTokens_areSeparatedOnlyWhereTheyWouldMerge() {
        Token int1 = token("int", 1, 1, 3);
        Token x = token("x", 1, 2, 2);                // starts left of where "int" ended
        int1.next = x;
        assertEquals("int x", node(int1, x).getSourceText());

        Token a = token("int", 1, 1, 3);
        Token plus = token("+", 1, 2, 2);
        a.next = plus;
        assertEquals("int+", node(a, plus).getSourceText());

        Token b = token("int", 1, 1, 3);
        Token empty = token("", 1, 2, 2);
        b.next = empty;
        assertEquals("int", node(b, empty).getSourceText());

        Token blank = token("", 1, 5, 9);
        Token y = token("y", 1, 2, 2);
        blank.next = y;
        assertEquals("y", node(blank, y).getSourceText());
    }
}
