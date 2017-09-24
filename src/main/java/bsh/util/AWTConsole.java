/*****************************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one                *
 * or more contributor license agreements.  See the NOTICE file              *
 * distributed with this work for additional information                     *
 * regarding copyright ownership.  The ASF licenses this file                *
 * to you under the Apache License, Version 2.0 (the                         *
 * "License"); you may not use this file except in compliance                *
 * with the License.  You may obtain a copy of the License at                *
 *                                                                           *
 *     http://www.apache.org/licenses/LICENSE-2.0                            *
 *                                                                           *
 * Unless required by applicable law or agreed to in writing,                *
 * software distributed under the License is distributed on an               *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY                    *
 * KIND, either express or implied.  See the License for the                 *
 * specific language governing permissions and limitations                   *
 * under the License.                                                        *
 *                                                                           *
 *                                                                           *
 * This file is part of the BeanShell Java Scripting distribution.           *
 * Documentation and updates may be found at http://www.beanshell.org/       *
 * Patrick Niemeyer (pat@pat.net)                                            *
 * Author of Learning Java, O'Reilly & Associates                            *
 *                                                                           *
 *****************************************************************************/
package bsh.util;

import java.awt.Color;
import java.awt.Font;
import java.awt.Frame;
import java.awt.TextArea;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.PrintStream;
import java.io.Reader;
import java.util.Vector;

import bsh.ConsoleInterface;
import bsh.Interpreter;

/*
    This should go away eventually...  Native AWT sucks.
    Use JConsole and the desktop() environment.

    Notes: todo -
    clean up the watcher thread, set daemon status
*/
/**
 * An old AWT based console for BeanShell.
 *
 * I looked everwhere for one, and couldn't find anything that worked.
 * I've tried to keep this as small as possible, no frills.
 * (Well, one frill - a simple history with the up/down arrows)
 * My hope is that this can be moved to a lightweight (portable) component
 * with JFC soon... but Swing is still very slow and buggy.
 *
 * Done: see JConsole.java
 *
 * The big Hack:
 *
 * The heinous, disguisting hack in here is to keep the caret (cursor)
 * at the bottom of the text (without the user having to constantly click
 * at the bottom). It wouldn't be so bad if the damned setCaretPostition()
 * worked as expected. But the AWT TextArea for some insane reason treats
 * NLs as characters... oh, and it refuses to let you set a caret position
 * greater than the text length - for which it counts NLs as *one* character.
 * The glorious hack to fix this is to go the TextComponent peer. I really
 * hate this.
 *
 * Out of date:
 *
 * This class is out of date. It does not use the special blocking piped
 * input stream that the jconsole uses.
 *
 * Deprecation:
 *
 * This file uses two deprecate APIs. We want to be a PrintStream so
 * that we can redirect stdout to our console... I don't see a way around
 * this. Also we have to use getPeer() for the big hack above.
 */
public class AWTConsole extends TextArea
        implements ConsoleInterface, Runnable, KeyListener {

    /** The Constant serialVersionUID. */
    private static final long serialVersionUID = 1L;
    /** The out pipe. */
    private OutputStream outPipe;
    /** The in pipe. */
    private InputStream inPipe;
    // formerly public
    /** The in. */
    private InputStream in;
    /** The out. */
    private PrintStream out;

    /** {@inheritDoc} */
    public Reader getIn() {
        return new InputStreamReader(this.in);
    }

    /** {@inheritDoc} */
    public PrintStream getOut() {
        return this.out;
    }

    /** {@inheritDoc} */
    public PrintStream getErr() {
        return this.out;
    }

    /** The line. */
    private StringBuffer line = new StringBuffer();
    /** The started line. */
    private String startedLine;
    /** The text length. */
    private int textLength = 0;
    /** The history. */
    private final Vector history = new Vector();
    /** The hist line. */
    private int histLine = 0;

    /**
     * Instantiates a new AWT console.
     *
     * @param rows
     *            the rows
     * @param cols
     *            the cols
     * @param cin
     *            the cin
     * @param cout
     *            the cout
     */
    public AWTConsole(final int rows, final int cols, final InputStream cin,
            final OutputStream cout) {
        super(rows, cols);
        this.setFont(new Font("Monospaced", Font.PLAIN, 14));
        this.setEditable(false);
        this.addKeyListener(this);
        this.outPipe = cout;
        if (this.outPipe == null) {
            this.outPipe = new PipedOutputStream();
            try {
                this.in = new PipedInputStream(
                        (PipedOutputStream) this.outPipe);
            } catch (final IOException e) {
                this.print("Console internal error...");
            }
        }
        // start the inpipe watcher
        this.inPipe = cin;
        new Thread(this).start();
        this.requestFocus();
    }

    /** {@inheritDoc} */
    public void keyPressed(final KeyEvent e) {
        this.type(e.getKeyCode(), e.getKeyChar(), e.getModifiers());
        e.consume();
    }

    /**
     * Instantiates a new AWT console.
     */
    public AWTConsole() {
        this(12, 80, null, null);
    }

    /**
     * Instantiates a new AWT console.
     *
     * @param in
     *            the in
     * @param out
     *            the out
     */
    public AWTConsole(final InputStream in, final OutputStream out) {
        this(12, 80, in, out);
    }

    /**
     * Type.
     *
     * @param code
     *            the code
     * @param ch
     *            the ch
     * @param modifiers
     *            the modifiers
     */
    public void type(final int code, final char ch, final int modifiers) {
        switch (code) {
            case KeyEvent.VK_BACK_SPACE:
                if (this.line.length() > 0) {
                    this.line.setLength(this.line.length() - 1);
                    this.replaceRange("", this.textLength - 1, this.textLength);
                    this.textLength--;
                }
                break;
            case KeyEvent.VK_ENTER:
                this.enter();
                break;
            case KeyEvent.VK_U:
                if ((modifiers & InputEvent.CTRL_MASK) > 0) {
                    final int len = this.line.length();
                    this.replaceRange("", this.textLength - len,
                            this.textLength);
                    this.line.setLength(0);
                    this.histLine = 0;
                    this.textLength = this.getText().length();
                } else
                    this.doChar(ch);
                break;
            case KeyEvent.VK_UP:
                this.historyUp();
                break;
            case KeyEvent.VK_DOWN:
                this.historyDown();
                break;
            case KeyEvent.VK_TAB:
                this.line.append("    ");
                this.append("    ");
                this.textLength += 4;
                break;
            /*
             * case (KeyEvent.VK_LEFT):
             * if (line.length() > 0) {
             * break;
             */
            case KeyEvent.VK_C: // Control-C
                if ((modifiers & InputEvent.CTRL_MASK) > 0) {
                    this.line.append("^C");
                    this.append("^C");
                    this.textLength += 2;
                } else
                    this.doChar(ch);
                break;
            default:
                this.doChar(ch);
        }
    }

    /**
     * Do char.
     *
     * @param ch
     *            the ch
     */
    private void doChar(final char ch) {
        if (ch >= ' ' && ch <= '~') {
            this.line.append(ch);
            this.append(String.valueOf(ch));
            this.textLength++;
        }
    }

    /**
     * Enter.
     */
    private void enter() {
        String s;
        if (this.line.length() == 0) // special hack for empty return!
            s = ";\n";
        else {
            s = this.line + "\n";
            this.history.addElement(this.line.toString());
        }
        this.line.setLength(0);
        this.histLine = 0;
        this.append("\n");
        this.textLength = this.getText().length(); // sync for safety
        this.acceptLine(s);
        this.setCaretPosition(this.textLength);
    }

    /** {@inheritDoc} *
     * Here's the really disguisting hack.
     * We have to get to the peer because TextComponent will refuse to
     * let us set us set a caret position greater than the text length.
     * Great. What a piece of crap.
     */
    @Override
    public void setCaretPosition(final int pos) {
        ((java.awt.peer.TextComponentPeer) this.getPeer())
                .setCaretPosition(pos + this.countNLs());
    }

    /**
     * Count N ls.
     *
     * @return the int
     *
     * This is part of a hack to fix the setCaretPosition() bug
     * Count the newlines in the text
     */
    private int countNLs() {
        final String s = this.getText();
        int c = 0;
        for (int i = 0; i < s.length(); i++)
            if (s.charAt(i) == '\n')
                c++;
        return c;
    }

    /**
     * History up.
     */
    private void historyUp() {
        if (this.history.size() == 0)
            return;
        if (this.histLine == 0) // save current line
            this.startedLine = this.line.toString();
        if (this.histLine < this.history.size()) {
            this.histLine++;
            this.showHistoryLine();
        }
    }

    /**
     * History down.
     */
    private void historyDown() {
        if (this.histLine == 0)
            return;
        this.histLine--;
        this.showHistoryLine();
    }

    /**
     * Show history line.
     */
    private void showHistoryLine() {
        String showline;
        if (this.histLine == 0)
            showline = this.startedLine;
        else
            showline = (String) this.history
                    .elementAt(this.history.size() - this.histLine);
        this.replaceRange(showline, this.textLength - this.line.length(),
                this.textLength);
        this.line = new StringBuffer(showline);
        this.textLength = this.getText().length();
    }

    /**
     * Accept line.
     *
     * @param line
     *            the line
     */
    private void acceptLine(final String line) {
        if (this.outPipe == null)
            this.print("Console internal error...");
        else
            try {
                this.outPipe.write(line.getBytes());
                this.outPipe.flush();
            } catch (final IOException e) {
                this.outPipe = null;
                throw new RuntimeException("Console pipe broken...");
            }
    }

    /** {@inheritDoc} */
    public void println(final Object o) {
        this.print(String.valueOf(o) + "\n");
    }

    /** {@inheritDoc} */
    public void error(final Object o) {
        this.print(o, Color.red);
    }

    /**
     * Prints No color.
     *
     * @param o
     *            the o
     * @param c
     *            the c
     */
    public void print(final Object o, final Color c) {
        this.print("*** " + String.valueOf(o));
    }

    /** {@inheritDoc} */
    public synchronized void print(final Object o) {
        this.append(String.valueOf(o));
        this.textLength = this.getText().length(); // sync for safety
    }

    /**
     * In pipe watcher.
     *
     * @throws IOException
     *             Signals that an I/O exception has occurred.
     */
    private void inPipeWatcher() throws IOException {
        if (this.inPipe == null) {
            final PipedOutputStream pout = new PipedOutputStream();
            this.out = new PrintStream(pout);
            this.inPipe = new PipedInputStream(pout);
        }
        final byte[] ba = new byte[256]; // arbitrary blocking factor
        int read;
        while ((read = this.inPipe.read(ba)) != -1)
            this.print(new String(ba, 0, read));
        this.println("Console: Input closed...");
    }

    /** {@inheritDoc} */
    public void run() {
        try {
            this.inPipeWatcher();
        } catch (final IOException e) {
            this.println("Console: I/O Error...");
        }
    }

    /**
     * The main method.
     *
     * @param args
     *            the arguments
     */
    public static void main(final String args[]) {
        final AWTConsole console = new AWTConsole();
        final Frame f = new Frame("Bsh Console");
        f.add(console, "Center");
        f.pack();
        f.setVisible(true);
        f.addWindowListener(new WindowAdapter() {

            @Override
            public void windowClosing(final WindowEvent e) {
                f.dispose();
            }
        });
        final Interpreter interpreter = new Interpreter(console);
        interpreter.run();
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        return "BeanShell AWTConsole";
    }

    /** {@inheritDoc} */
    public void keyTyped(final KeyEvent e) {}

    /** {@inheritDoc} */
    public void keyReleased(final KeyEvent e) {}
}
