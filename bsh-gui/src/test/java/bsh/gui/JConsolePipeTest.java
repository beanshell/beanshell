package bsh.gui;

import static bsh.gui.JConsoleTest.press;
import static bsh.gui.JConsoleTest.textPane;
import static bsh.gui.JConsoleTest.typeCommand;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.awt.event.KeyEvent;
import java.io.InputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;

import org.junit.Test;

public class JConsolePipeTest {

    @Test
    public void entering_command_larger_than_pipe_does_not_block_event_thread() throws Exception {
        final JConsole console = new JConsole();
        final JTextPane text = textPane(console);
        final InputStream in = console.getInputStream();
        StringBuilder big = new StringBuilder();
        while (big.length() < 200 * 1024)
            big.append("x = 1; ");
        final String command = big.toString();
        final int expected = command.length() + 1;
        final AtomicInteger received = new AtomicInteger();
        final CountDownLatch allReceived = new CountDownLatch(1);

        // Like the interpreter: read a little, then print to the console before reading on.
        Thread reader = new Thread(() -> {
            try {
                if (in.read() != -1)
                    received.incrementAndGet();
                console.print("result\n");
                byte[] buf = new byte[8192];
                int n;
                while (received.get() < expected && (n = in.read(buf)) != -1)
                    received.addAndGet(n);
                allReceived.countDown();
            } catch (Exception e) {
                // leaves allReceived unreleased, failing the test
            }
        });
        reader.setDaemon(true);
        reader.start();

        final CountDownLatch entered = new CountDownLatch(1);
        SwingUtilities.invokeLater(() -> {
            try {
                typeCommand(text, command);
            } catch (BadLocationException e) {
                throw new IllegalStateException(e);
            }
            press(console, KeyEvent.VK_ENTER);
            entered.countDown();
        });

        assertTrue("event thread blocked writing the command", entered.await(20, TimeUnit.SECONDS));
        assertTrue("reader did not receive the whole command", allReceived.await(20, TimeUnit.SECONDS));
        assertEquals(expected, received.get());
    }
}
