package bsh.gui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.awt.event.KeyEvent;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.stream.Collectors;

import javax.swing.JTextPane;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultEditorKit;
import javax.swing.text.Document;

import org.junit.Test;

public class JConsoleTest {

    static JTextPane textPane(JConsole console) {
        return (JTextPane) console.getViewport().getView();
    }

    static String documentText(JTextPane text) throws BadLocationException {
        Document doc = text.getDocument();
        return doc.getText(0, doc.getLength());
    }

    static void typeCommand(JTextPane text, String command) throws BadLocationException {
        Document doc = text.getDocument();
        doc.insertString(doc.getLength(), command, null);
    }

    static void press(JConsole console, int keyCode) {
        console.keyPressed(new KeyEvent(textPane(console), KeyEvent.KEY_PRESSED,
                0, 0, keyCode, KeyEvent.CHAR_UNDEFINED));
    }

    private static void tab(JConsole console) {
        console.keyReleased(new KeyEvent(textPane(console), KeyEvent.KEY_RELEASED,
                0, 0, KeyEvent.VK_TAB, '\t'));
    }

    private static String readLine(InputStream in) throws Exception {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) != -1) {
            line.write(b);
            if (b == '\n')
                break;
        }
        return new String(line.toByteArray(), StandardCharsets.UTF_8);
    }

    @Test
    public void ambiguous_completion_keeps_command_boundary_with_crlf_line_endings() throws Exception {
        JConsole console = new JConsole();
        JTextPane text = textPane(console);
        text.getDocument().putProperty(DefaultEditorKit.EndOfLineStringProperty, "\r\n");
        console.setNameCompletion(part -> new String[] {"print", "printBanner"});
        console.print("a\nb\nc\nd\nbsh % ");
        typeCommand(text, "pr");

        tab(console);
        press(console, KeyEvent.VK_ENTER);

        assertEquals("pr\n", readLine(console.getInputStream()));
    }

    @Test
    public void ambiguous_completion_reprints_prompt_on_first_line_intact() throws Exception {
        JConsole console = new JConsole();
        JTextPane text = textPane(console);
        console.setNameCompletion(part -> new String[] {"print", "printBanner"});
        console.print("bsh % ");
        typeCommand(text, "pr");

        tab(console);

        assertTrue(documentText(text), documentText(text).endsWith("\nbsh % pr"));
    }

    @Test
    public void added_history_is_recalled_with_up_arrow() throws Exception {
        JConsole console = new JConsole();
        JTextPane text = textPane(console);
        console.print("bsh % ");
        console.addHistory("preloaded();");

        press(console, KeyEvent.VK_UP);

        assertTrue(documentText(text), documentText(text).endsWith("bsh % preloaded();"));
    }

    @Test
    public void close_terminates_pipe_writer_thread() throws Exception {
        Set<Thread> before = pipeWriterThreads();
        JConsole console = new JConsole();
        JTextPane text = textPane(console);
        console.print("bsh % ");
        typeCommand(text, "x=1");
        press(console, KeyEvent.VK_ENTER); // submit to the pipe writer
        // Wait for the writer thread to appear (it is created lazily).
        Set<Thread> during = before;
        for (int i = 0; i < 50 && during.equals(before); i++) {
            Thread.sleep(100);
            during = pipeWriterThreads();
        }
        console.close();
        // await termination with retries
        Set<Thread> after = pipeWriterThreads();
        for (int i = 0; i < 50 && !after.equals(before); i++) {
            Thread.sleep(100);
            after = pipeWriterThreads();
        }
        assertEquals("pipe writer thread leaked after close", before, after);
    }

    @Test
    public void enter_after_close_does_not_throw() throws Exception {
        JConsole console = new JConsole();
        JTextPane text = textPane(console);
        console.print("bsh % ");
        typeCommand(text, "x=1");

        console.close();

        press(console, KeyEvent.VK_ENTER); // must not throw RejectedExecutionException
    }

    private static Set<Thread> pipeWriterThreads() {
        return Thread.getAllStackTraces().keySet().stream()
                .filter(t -> "JConsole pipe writer".equals(t.getName()) && t.isAlive())
                .collect(Collectors.toSet());
    }
}
