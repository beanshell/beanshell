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
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.PrintStream;
import java.io.Reader;
import java.util.Vector;

import javax.swing.Icon;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;

/**
 * A JFC/Swing based console for the BeanShell desktop.
 * This is a descendant of the old AWTConsole.
 *
 * Improvements by: Mark Donszelmann <Mark.Donszelmann@cern.ch>
 * including Cut & Paste
 *
 * Improvements by: Daniel Leuck
 * including Color and Image support, key press bug workaround
 */
public class JConsole extends JScrollPane
        implements GUIConsoleInterface, Runnable, KeyListener, MouseListener,
        ActionListener, PropertyChangeListener {

    /** The Constant serialVersionUID. */
    private static final long serialVersionUID = 1L;
    /** The Constant CUT. */
    private static final String CUT = "Cut";
    /** The Constant COPY. */
    private static final String COPY = "Copy";
    /** The Constant PASTE. */
    private static final String PASTE = "Paste";
    /** The out pipe. */
    private OutputStream outPipe;
    /** The in pipe. */
    private InputStream inPipe;
    /** The in. */
    private InputStream in;
    /** The out. */
    private PrintStream out;

    /**
     * Gets the input stream.
     *
     * @return the input stream
     */
    public InputStream getInputStream() {
        return this.in;
    }

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

    /** The cmd start. */
    private int cmdStart = 0;
    /** The history. */
    private final Vector history = new Vector();
    /** The started line. */
    private String startedLine;
    /** The hist line. */
    private int histLine = 0;
    /** The menu. */
    private final JPopupMenu menu;
    /** The text. */
    private final JTextPane text;
    /** The doc. */
    private DefaultStyledDocument doc;
    /** The name completion. */
    NameCompletion nameCompletion;
    /** The show ambig max. */
    final int SHOW_AMBIG_MAX = 10;
    /** The got up. */
    // hack to prevent key repeat for some reason?
    private boolean gotUp = true;

    /**
     * Instantiates a new j console.
     */
    public JConsole() {
        this(null, null);
    }

    /**
     * Instantiates a new j console.
     *
     * @param cin
     *            the cin
     * @param cout
     *            the cout
     */
    public JConsole(final InputStream cin, final OutputStream cout) {
        super();
        // Special TextPane which catches for cut and paste, both L&F keys and
        // programmatic behaviour
        this.text = new JTextPane(this.doc = new DefaultStyledDocument()) {

            /**
             *
             */
            private static final long serialVersionUID = 1L;

            @Override
            public void cut() {
                if (JConsole.this.text
                        .getCaretPosition() < JConsole.this.cmdStart)
                    super.copy();
                else
                    super.cut();
            }

            @Override
            public void paste() {
                JConsole.this.forceCaretMoveToEnd();
                super.paste();
            }
        };
        final Font font = new Font("Monospaced", Font.PLAIN, 14);
        this.text.setText("");
        this.text.setFont(font);
        this.text.setMargin(new Insets(7, 5, 7, 5));
        this.text.addKeyListener(this);
        this.setViewportView(this.text);
        // create popup menu
        this.menu = new JPopupMenu("JConsole Menu");
        this.menu.add(new JMenuItem(CUT)).addActionListener(this);
        this.menu.add(new JMenuItem(COPY)).addActionListener(this);
        this.menu.add(new JMenuItem(PASTE)).addActionListener(this);
        this.text.addMouseListener(this);
        // make sure popup menu follows Look & Feel
        UIManager.addPropertyChangeListener(this);
        this.outPipe = cout;
        if (this.outPipe == null) {
            this.outPipe = new PipedOutputStream();
            try {
                this.in = new PipedInputStream(
                        (PipedOutputStream) this.outPipe);
            } catch (final IOException e) {
                this.print("Console internal error (1)...", Color.red);
            }
        }
        this.inPipe = cin;
        if (this.inPipe == null) {
            final PipedOutputStream pout = new PipedOutputStream();
            this.out = new PrintStream(pout);
            try {
                this.inPipe = new BlockingPipedInputStream(pout);
            } catch (final IOException e) {
                this.print("Console internal error: " + e);
            }
        }
        // Start the inpipe watcher
        new Thread(this).start();
        this.requestFocus();
    }

    /** {@inheritDoc} */
    @Override
    public void requestFocus() {
        super.requestFocus();
        this.text.requestFocus();
    }

    /** {@inheritDoc} */
    public void keyPressed(final KeyEvent e) {
        this.type(e);
        this.gotUp = false;
    }

    /** {@inheritDoc} */
    public void keyTyped(final KeyEvent e) {
        this.type(e);
    }

    /** {@inheritDoc} */
    public void keyReleased(final KeyEvent e) {
        this.gotUp = true;
        this.type(e);
    }

    /**
     * Type.
     *
     * @param e
     *            the e
     */
    private synchronized void type(final KeyEvent e) {
        switch (e.getKeyCode()) {
            case KeyEvent.VK_ENTER:
                if (e.getID() == KeyEvent.KEY_PRESSED)
                    if (this.gotUp) {
                        this.enter();
                        this.resetCommandStart();
                        this.text.setCaretPosition(this.cmdStart);
                    }
                e.consume();
                this.text.repaint();
                break;
            case KeyEvent.VK_UP:
                if (e.getID() == KeyEvent.KEY_PRESSED)
                    this.historyUp();
                e.consume();
                break;
            case KeyEvent.VK_DOWN:
                if (e.getID() == KeyEvent.KEY_PRESSED)
                    this.historyDown();
                e.consume();
                break;
            case KeyEvent.VK_LEFT:
            case KeyEvent.VK_BACK_SPACE:
            case KeyEvent.VK_DELETE:
                if (this.text.getCaretPosition() <= this.cmdStart)
                    // This doesn't work for backspace.
                    // See default case for workaround
                    e.consume();
                break;
            case KeyEvent.VK_RIGHT:
                this.forceCaretMoveToStart();
                break;
            case KeyEvent.VK_HOME:
                this.text.setCaretPosition(this.cmdStart);
                e.consume();
                break;
            case KeyEvent.VK_U: // clear line
                if ((e.getModifiers() & InputEvent.CTRL_MASK) > 0) {
                    this.replaceRange("", this.cmdStart, this.textLength());
                    this.histLine = 0;
                    e.consume();
                }
                break;
            case KeyEvent.VK_ALT:
            case KeyEvent.VK_CAPS_LOCK:
            case KeyEvent.VK_CONTROL:
            case KeyEvent.VK_META:
            case KeyEvent.VK_SHIFT:
            case KeyEvent.VK_PRINTSCREEN:
            case KeyEvent.VK_SCROLL_LOCK:
            case KeyEvent.VK_PAUSE:
            case KeyEvent.VK_INSERT:
            case KeyEvent.VK_F1:
            case KeyEvent.VK_F2:
            case KeyEvent.VK_F3:
            case KeyEvent.VK_F4:
            case KeyEvent.VK_F5:
            case KeyEvent.VK_F6:
            case KeyEvent.VK_F7:
            case KeyEvent.VK_F8:
            case KeyEvent.VK_F9:
            case KeyEvent.VK_F10:
            case KeyEvent.VK_F11:
            case KeyEvent.VK_F12:
            case KeyEvent.VK_ESCAPE:
                // only modifier pressed
                break;
            // Control-C
            case KeyEvent.VK_C:
                if (this.text.getSelectedText() == null) {
                    if ((e.getModifiers() & InputEvent.CTRL_MASK) > 0
                            && e.getID() == KeyEvent.KEY_PRESSED)
                        this.append("^C");
                    e.consume();
                }
                break;
            case KeyEvent.VK_TAB:
                if (e.getID() == KeyEvent.KEY_RELEASED) {
                    final String part = this.text.getText()
                            .substring(this.cmdStart);
                    this.doCommandCompletion(part);
                }
                e.consume();
                break;
            default:
                if ((e.getModifiers() & (InputEvent.CTRL_MASK
                        | InputEvent.ALT_MASK | InputEvent.META_MASK)) == 0)
                    // plain character
                    this.forceCaretMoveToEnd();
                /*
                 * The getKeyCode function always returns VK_UNDEFINED for
                 * keyTyped events, so backspace is not fully consumed.
                 */
                if (e.paramString().indexOf("Backspace") != -1)
                    if (this.text.getCaretPosition() <= this.cmdStart) {
                        e.consume();
                        break;
                    }
                break;
        }
    }

    /**
     * Do command completion.
     *
     * @param part
     *            the part
     */
    private void doCommandCompletion(String part) {
        if (this.nameCompletion == null)
            return;
        int i = part.length() - 1;
        // Character.isJavaIdentifierPart() How convenient for us!!
        while (i >= 0 && (Character.isJavaIdentifierPart(part.charAt(i))
                || part.charAt(i) == '.'))
            i--;
        part = part.substring(i + 1);
        if (part.length() < 2) // reasonable completion length
            return;
        // System.out.println("completing part: "+part);
        // no completion
        final String[] complete = this.nameCompletion.completeName(part);
        if (complete.length == 0) {
            java.awt.Toolkit.getDefaultToolkit().beep();
            return;
        }
        // Found one completion (possibly what we already have)
        if (complete.length == 1 && !complete.equals(part)) {
            final String append = complete[0].substring(part.length());
            this.append(append);
            return;
        }
        // Found ambiguous, show (some of) them
        final String line = this.text.getText();
        final String command = line.substring(this.cmdStart);
        // Find prompt
        for (i = this.cmdStart; line.charAt(i) != '\n' && i > 0; i--);
        final String prompt = line.substring(i + 1, this.cmdStart);
        // Show ambiguous
        final StringBuffer sb = new StringBuffer("\n");
        for (i = 0; i < complete.length && i < this.SHOW_AMBIG_MAX; i++)
            sb.append(complete[i] + "\n");
        if (i == this.SHOW_AMBIG_MAX)
            sb.append("...\n");
        this.print(sb, Color.gray);
        this.print(prompt); // print resets command start
        this.append(command); // append does not reset command start
    }

    /**
     * Reset command start.
     */
    private void resetCommandStart() {
        this.cmdStart = this.textLength();
    }

    /**
     * Append.
     *
     * @param string
     *            the string
     */
    private void append(final String string) {
        final int slen = this.textLength();
        this.text.select(slen, slen);
        this.text.replaceSelection(string);
    }

    /**
     * Replace range.
     *
     * @param s
     *            the s
     * @param start
     *            the start
     * @param end
     *            the end
     * @return the string
     */
    private String replaceRange(final Object s, final int start,
            final int end) {
        final String st = s.toString();
        this.text.select(start, end);
        this.text.replaceSelection(st);
        // text.repaint();
        return st;
    }

    /**
     * Force caret move to end.
     */
    private void forceCaretMoveToEnd() {
        if (this.text.getCaretPosition() < this.cmdStart)
            // move caret first!
            this.text.setCaretPosition(this.textLength());
        this.text.repaint();
    }

    /**
     * Force caret move to start.
     */
    private void forceCaretMoveToStart() {
        if (this.text.getCaretPosition() < this.cmdStart) {
            // move caret first!
        }
        this.text.repaint();
    }

    /**
     * Enter.
     */
    private void enter() {
        String s = this.getCmd();
        if (s.length() == 0) // special hack for empty return!
            s = ";\n";
        else {
            this.history.addElement(s);
            s = s + "\n";
        }
        this.append("\n");
        this.histLine = 0;
        this.acceptLine(s);
        this.text.repaint();
    }

    /**
     * Gets the cmd.
     *
     * @return the cmd
     */
    private String getCmd() {
        String s = "";
        try {
            s = this.text.getText(this.cmdStart,
                    this.textLength() - this.cmdStart);
        } catch (final BadLocationException e) {
            // should not happen
            System.out.println("Internal JConsole Error: " + e);
        }
        return s;
    }

    /**
     * History up.
     */
    private void historyUp() {
        if (this.history.size() == 0)
            return;
        if (this.histLine == 0) // save current line
            this.startedLine = this.getCmd();
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
        this.replaceRange(showline, this.cmdStart, this.textLength());
        this.text.setCaretPosition(this.textLength());
        this.text.repaint();
    }

    /** The zeros. */
    String ZEROS = "000";

    /**
     * Accept line.
     *
     * @param line
     *            the line
     */
    private void acceptLine(String line) {
        // Patch to handle Unicode characters
        // Submitted by Daniel Leuck
        final StringBuffer buf = new StringBuffer();
        final int lineLength = line.length();
        for (int i = 0; i < lineLength; i++) {
            final char c = line.charAt(i);
            if (c > 127) {
                String val = Integer.toString(c, 16);
                val = this.ZEROS.substring(0, 4 - val.length()) + val;
                buf.append("\\u" + val);
            } else
                buf.append(c);
        }
        line = buf.toString();
        // End unicode patch
        if (this.outPipe == null)
            this.print("Console internal error: cannot output ...", Color.red);
        else
            try {
                this.outPipe.write(line.getBytes());
                this.outPipe.flush();
            } catch (final IOException e) {
                this.outPipe = null;
                throw new RuntimeException("Console pipe broken...");
            }
        // text.repaint();
    }

    /** {@inheritDoc} */
    public void println(final Object o) {
        this.print(String.valueOf(o) + "\n");
        this.text.repaint();
    }

    /** {@inheritDoc} */
    public void print(final Object o) {
        this.invokeAndWait(new Runnable() {

            public void run() {
                JConsole.this.append(String.valueOf(o));
                JConsole.this.resetCommandStart();
                JConsole.this.text.setCaretPosition(JConsole.this.cmdStart);
            }
        });
    }

    /**
     * Prints "\\n" (i.e. newline)
     */
    public void println() {
        this.print("\n");
        this.text.repaint();
    }

    /** {@inheritDoc} */
    public void error(final Object o) {
        this.print(o, Color.red);
    }

    /**
     * Println.
     *
     * @param icon
     *            the icon
     */
    public void println(final Icon icon) {
        this.print(icon);
        this.println();
        this.text.repaint();
    }

    /**
     * Prints the.
     *
     * @param icon
     *            the icon
     */
    public void print(final Icon icon) {
        if (icon == null)
            return;
        this.invokeAndWait(new Runnable() {

            public void run() {
                JConsole.this.text.insertIcon(icon);
                JConsole.this.resetCommandStart();
                JConsole.this.text.setCaretPosition(JConsole.this.cmdStart);
            }
        });
    }

    /**
     * Prints the.
     *
     * @param s
     *            the s
     * @param font
     *            the font
     */
    public void print(final Object s, final Font font) {
        this.print(s, font, null);
    }

    /** {@inheritDoc} */
    public void print(final Object s, final Color color) {
        this.print(s, null, color);
    }

    /**
     * Prints the.
     *
     * @param o
     *            the o
     * @param font
     *            the font
     * @param color
     *            the color
     */
    public void print(final Object o, final Font font, final Color color) {
        this.invokeAndWait(new Runnable() {

            public void run() {
                final AttributeSet old = JConsole.this.getStyle();
                JConsole.this.setStyle(font, color);
                JConsole.this.append(String.valueOf(o));
                JConsole.this.resetCommandStart();
                JConsole.this.text.setCaretPosition(JConsole.this.cmdStart);
                JConsole.this.setStyle(old, true);
            }
        });
    }

    /**
     * Prints the.
     *
     * @param s
     *            the s
     * @param fontFamilyName
     *            the font family name
     * @param size
     *            the size
     * @param color
     *            the color
     */
    public void print(final Object s, final String fontFamilyName,
            final int size, final Color color) {
        this.print(s, fontFamilyName, size, color, false, false, false);
    }

    /**
     * Prints the.
     *
     * @param o
     *            the o
     * @param fontFamilyName
     *            the font family name
     * @param size
     *            the size
     * @param color
     *            the color
     * @param bold
     *            the bold
     * @param italic
     *            the italic
     * @param underline
     *            the underline
     */
    public void print(final Object o, final String fontFamilyName,
            final int size, final Color color, final boolean bold,
            final boolean italic, final boolean underline) {
        this.invokeAndWait(new Runnable() {

            public void run() {
                final AttributeSet old = JConsole.this.getStyle();
                JConsole.this.setStyle(fontFamilyName, size, color, bold,
                        italic, underline);
                JConsole.this.append(String.valueOf(o));
                JConsole.this.resetCommandStart();
                JConsole.this.text.setCaretPosition(JConsole.this.cmdStart);
                JConsole.this.setStyle(old, true);
            }
        });
    }

    /**
     * Sets the style.
     *
     * @param font
     *            the font
     * @param color
     *            the color
     * @return the attribute set
     */
    private AttributeSet setStyle(final Font font, final Color color) {
        if (font != null)
            return this.setStyle(font.getFamily(), font.getSize(), color,
                    font.isBold(), font.isItalic(),
                    StyleConstants.isUnderline(this.getStyle()));
        else
            return this.setStyle(null, -1, color);
    }

    /**
     * Sets the style.
     *
     * @param fontFamilyName
     *            the font family name
     * @param size
     *            the size
     * @param color
     *            the color
     * @return the attribute set
     */
    private AttributeSet setStyle(final String fontFamilyName, final int size,
            final Color color) {
        final MutableAttributeSet attr = new SimpleAttributeSet();
        if (color != null)
            StyleConstants.setForeground(attr, color);
        if (fontFamilyName != null)
            StyleConstants.setFontFamily(attr, fontFamilyName);
        if (size != -1)
            StyleConstants.setFontSize(attr, size);
        this.setStyle(attr);
        return this.getStyle();
    }

    /**
     * Sets the style.
     *
     * @param fontFamilyName
     *            the font family name
     * @param size
     *            the size
     * @param color
     *            the color
     * @param bold
     *            the bold
     * @param italic
     *            the italic
     * @param underline
     *            the underline
     * @return the attribute set
     */
    private AttributeSet setStyle(final String fontFamilyName, final int size,
            final Color color, final boolean bold, final boolean italic,
            final boolean underline) {
        final MutableAttributeSet attr = new SimpleAttributeSet();
        if (color != null)
            StyleConstants.setForeground(attr, color);
        if (fontFamilyName != null)
            StyleConstants.setFontFamily(attr, fontFamilyName);
        if (size != -1)
            StyleConstants.setFontSize(attr, size);
        StyleConstants.setBold(attr, bold);
        StyleConstants.setItalic(attr, italic);
        StyleConstants.setUnderline(attr, underline);
        this.setStyle(attr);
        return this.getStyle();
    }

    /**
     * Sets the style.
     *
     * @param attributes
     *            the new style
     */
    private void setStyle(final AttributeSet attributes) {
        this.setStyle(attributes, false);
    }

    /**
     * Sets the style.
     *
     * @param attributes
     *            the attributes
     * @param overWrite
     *            the over write
     */
    private void setStyle(final AttributeSet attributes,
            final boolean overWrite) {
        this.text.setCharacterAttributes(attributes, overWrite);
    }

    /**
     * Gets the style.
     *
     * @return the style
     */
    private AttributeSet getStyle() {
        return this.text.getCharacterAttributes();
    }

    /** {@inheritDoc} */
    @Override
    public void setFont(final Font font) {
        super.setFont(font);
        if (this.text != null)
            this.text.setFont(font);
    }

    /**
     * In pipe watcher.
     *
     * @throws IOException
     *             Signals that an I/O exception has occurred.
     */
    private void inPipeWatcher() throws IOException {
        final byte[] ba = new byte[256]; // arbitrary blocking factor
        int read;
        while ((read = this.inPipe.read(ba)) != -1)
            this.print(new String(ba, 0, read));
        // text.repaint();
        this.println("Console: Input closed...");
    }

    /** {@inheritDoc} */
    public void run() {
        try {
            this.inPipeWatcher();
        } catch (final IOException e) {
            this.print("Console: I/O Error: " + e + "\n", Color.red);
        }
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        return "BeanShell console";
    }

    /** {@inheritDoc} */
    public void mouseClicked(final MouseEvent event) {}

    /** {@inheritDoc} */
    public void mousePressed(final MouseEvent event) {
        if (event.isPopupTrigger())
            this.menu.show((Component) event.getSource(), event.getX(),
                    event.getY());
    }

    /** {@inheritDoc} */
    public void mouseReleased(final MouseEvent event) {
        if (event.isPopupTrigger())
            this.menu.show((Component) event.getSource(), event.getX(),
                    event.getY());
        this.text.repaint();
    }

    /** {@inheritDoc} */
    public void mouseEntered(final MouseEvent event) {}

    /** {@inheritDoc} */
    public void mouseExited(final MouseEvent event) {}

    /** {@inheritDoc} */
    public void propertyChange(final PropertyChangeEvent event) {
        if (event.getPropertyName().equals("lookAndFeel"))
            SwingUtilities.updateComponentTreeUI(this.menu);
    }

    /** {@inheritDoc} */
    public void actionPerformed(final ActionEvent event) {
        final String cmd = event.getActionCommand();
        if (cmd.equals(CUT))
            this.text.cut();
        else if (cmd.equals(COPY))
            this.text.copy();
        else if (cmd.equals(PASTE))
            this.text.paste();
    }

    /**
     * If not in the event thread run via SwingUtilities.invokeAndWait().
     *
     * @param run
     *            the run
     */
    private void invokeAndWait(final Runnable run) {
        if (!SwingUtilities.isEventDispatchThread())
            try {
                SwingUtilities.invokeAndWait(run);
            } catch (final Exception e) {
                // shouldn't happen
                e.printStackTrace();
            }
        else
            run.run();
    }

    /**
     * The overridden read method in this class will not throw "Broken pipe"
     * IOExceptions; It will simply wait for new writers and data.
     * This is used by the JConsole internal read thread to allow writers
     * in different (and in particular ephemeral) threads to write to the pipe.
     *
     * It also checks a little more frequently than the original read().
     *
     * Warning: read() will not even error on a read to an explicitly closed
     * pipe (override closed to for that).
     */
    public static class BlockingPipedInputStream extends PipedInputStream {

        /** The closed. */
        boolean closed;

        /**
         * Instantiates a new blocking piped input stream.
         *
         * @param pout
         *            the pout
         * @throws IOException
         *             Signals that an I/O exception has occurred.
         */
        public BlockingPipedInputStream(final PipedOutputStream pout)
                throws IOException {
            super(pout);
        }

        /** {@inheritDoc} */
        @Override
        public synchronized int read() throws IOException {
            if (this.closed)
                throw new IOException("stream closed");
            while (super.in < 0) { // While no data */
                this.notifyAll(); // Notify any writers to wake up
                try {
                    this.wait(750);
                } catch (final InterruptedException e) {
                    throw new InterruptedIOException();
                }
            }
            // This is what the superclass does.
            final int ret = this.buffer[super.out++] & 0xFF;
            if (super.out >= this.buffer.length)
                super.out = 0;
            if (super.in == super.out)
                super.in = -1; /* now empty */
            return ret;
        }

        /** {@inheritDoc} */
        @Override
        public void close() throws IOException {
            this.closed = true;
            super.close();
        }
    }

    /** {@inheritDoc} */
    public void setNameCompletion(final NameCompletion nc) {
        this.nameCompletion = nc;
    }

    /** {@inheritDoc} */
    public void setWaitFeedback(final boolean on) {
        if (on)
            this.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        else
            this.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
    }

    /**
     * Text length.
     *
     * @return the int
     */
    private int textLength() {
        return this.text.getDocument().getLength();
    }
}
