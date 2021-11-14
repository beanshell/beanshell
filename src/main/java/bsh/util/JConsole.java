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
import java.awt.event.ActionEvent;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseListener;
import java.awt.event.ComponentAdapter;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.PrintStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.AbstractAction;
import javax.swing.UIManager;
import javax.swing.Icon;
import javax.swing.SwingUtilities;
import javax.swing.text.AttributeSet;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;

import bsh.FileReader;
import org.syntax.jedit.InputHandler;

/**
    A JFC/Swing based console for the BeanShell desktop.
    This is a descendant of the old AWTConsole.

    Improvements by: Mark Donszelmann <Mark.Donszelmann@cern.ch>
        including Cut & Paste

    Improvements by: Daniel Leuck
        including Color and Image support, key press bug workaround
*/
public class JConsole extends JPanel
    implements GUIConsoleInterface, Runnable, MouseListener, ActionListener, PropertyChangeListener
{
    private final static String CUT = "Cut";
    private final static String COPY = "Copy";
    private final static String PASTE = "Paste";

    private OutputStream outPipe;
    private InputStream inPipe;
    private InputStream in;
    private PrintStream out;

    public InputStream getInputStream() { return in; }
    public Reader getIn() { return new FileReader(in); }
    public PrintStream getOut() { return out;   }
    public PrintStream getErr() { return out;   }

    private int cmdStart = 0;
    private List<String> history = new ArrayList<>();
    private String startedLine;
    private int histLine = 0;

    private JPopupMenu menu;
    private de.netsysit.ui.text.AugmentedJEditTextArea text;

    NameCompletion nameCompletion;
    final int SHOW_AMBIG_MAX = 10;

    // hack to prevent key repeat for some reason?
    private boolean gotUp = true;

   public JConsole() {
        this(null, null);
    }

    public JConsole( InputStream cin, OutputStream cout )
    {
        super(new java.awt.BorderLayout());

        de.netsysit.ui.text.JavaEditor je=new de.netsysit.ui.text.JavaEditor();
        de.netsysit.ui.text.AugmentedJEditTextArea jta=je.getTextField();
        text=jta;
        add(text);
        text.getInputHandler().addKeyBinding("ENTER", new AbstractAction()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                enter();
                resetCommandStart();
                text.setCaretPosition(cmdStart);
            }
        });
        text.getInputHandler().addKeyBinding("UP", new AbstractAction()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                historyUp();
            }
        });
        text.getInputHandler().addKeyBinding("DOWN", new AbstractAction()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                historyDown();
            }
        });
        text.getInputHandler().addKeyBinding("TAB", new AbstractAction()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                String part = text.getText();
                if ( null == part )
                    ;
                else
                    doCommandCompletion( part.substring( cmdStart ) );
            }
        });
        text.getInputHandler().addKeyBinding("BACK_SPACE", new AbstractAction()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                if(text.getCaretPosition()>cmdStart)
                    org.syntax.jedit.InputHandler.BACKSPACE.actionPerformed(e);
                else
                    java.awt.Toolkit.getDefaultToolkit().beep();
            }
        });
        text.getInputHandler().addKeyBinding("C+BACK_SPACE", new AbstractAction()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                if(text.getCaretPosition()>cmdStart)
                    InputHandler.BACKSPACE_WORD.actionPerformed(e);
                else
                    java.awt.Toolkit.getDefaultToolkit().beep();
            }
        });
        text.getInputHandler().addKeyBinding("LEFT", new AbstractAction()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                if(text.getCaretPosition()>cmdStart)
                    InputHandler.PREV_CHAR.actionPerformed(e);
                else
                    java.awt.Toolkit.getDefaultToolkit().beep();
            }
        });
        text.getInputHandler().addKeyBinding("S+LEFT", new AbstractAction()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                if(text.getCaretPosition()>cmdStart)
                    InputHandler.SELECT_PREV_CHAR.actionPerformed(e);
                else
                    java.awt.Toolkit.getDefaultToolkit().beep();
            }
        });
        text.getInputHandler().addKeyBinding("C+LEFT", new AbstractAction()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                if(text.getCaretPosition()>cmdStart)
                    InputHandler.PREV_WORD.actionPerformed(e);
                else
                    java.awt.Toolkit.getDefaultToolkit().beep();
            }
        });
        text.getInputHandler().addKeyBinding("CS+LEFT", new AbstractAction()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                if(text.getCaretPosition()>cmdStart)
                    InputHandler.SELECT_PREV_WORD.actionPerformed(e);
                else
                    java.awt.Toolkit.getDefaultToolkit().beep();
            }
        });
        text.getInputHandler().addKeyBinding("HOME", new AbstractAction()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                text.setCaretPosition(cmdStart);
            }
        });
        text.getInputHandler().addKeyBinding("C+HOME", new AbstractAction()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                text.setCaretPosition(cmdStart);
            }
        });
        text.getInputHandler().addKeyBinding("S+HOME", new AbstractAction()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                if(cmdStart==text.getCaretPosition())
                    java.awt.Toolkit.getDefaultToolkit().beep();
                else
                {
                    text.select(text.getMarkPosition(),cmdStart);
                }
            }
        });
        text.getInputHandler().addKeyBinding("CS+HOME", new AbstractAction()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                if(cmdStart==text.getCaretPosition())
                    java.awt.Toolkit.getDefaultToolkit().beep();
                else
                {
                    text.select(text.getMarkPosition(),cmdStart);
                }
            }
        });

/*        // Special TextPane which catches for cut and paste, both L&F keys and
        // programmatic behaviour
        text = new JTextPane( new DefaultStyledDocument() ) {
                private static final long serialVersionUID = 1L;

                public void cut() {
                    if (text.getCaretPosition() < cmdStart) {
                        super.copy();
                    } else {
                        super.cut();
                    }
                }

                public void paste() {
                    forceCaretMoveToEnd();
                    super.paste();
                }
            };
*/
        Font font = new Font("Monospaced",Font.PLAIN,14);
        text.setText("");
        text.setFont( font );
//        text.setMargin( new Insets(7,5,7,5) );
//        text.addKeyListener(this);
//        setViewportView(text);

/*        // create popup menu
        menu = new JPopupMenu("JConsole Menu");
        menu.add(new JMenuItem(CUT)).addActionListener(this);
        menu.add(new JMenuItem(COPY)).addActionListener(this);
        menu.add(new JMenuItem(PASTE)).addActionListener(this);

        text.addMouseListener(this);
*/
        // make sure popup menu follows Look & Feel
        UIManager.addPropertyChangeListener(this);

        outPipe = cout;
        if ( outPipe == null ) {
            outPipe = new PipedOutputStream();
            try {
                in = new PipedInputStream((PipedOutputStream) outPipe, 64 * 1024);
            } catch ( IOException e ) {
                print("Console internal error (1)...", Color.red);
            }
        }

        inPipe = cin;
        if ( inPipe == null ) {
            PipedOutputStream pout = new PipedOutputStream();
            try {
                out = new PrintStream( pout, true, "UTF-8" );
                inPipe = new BlockingPipedInputStream(pout);
            } catch ( IOException e ) { print("Console internal error: "+e); }
        }
        // Start the inpipe watcher
        new Thread( this ).start();

        requestFocus();
        text.addComponentListener(new ComponentAdapter()
        {
            @Override
            public void componentResized(ComponentEvent e)
            {
                //This is needed to get the Component focused!!
                super.componentResized(e);
                text.requestFocusInWindow();
            }

        });
    }

    public void requestFocus()
    {
        super.requestFocus();
        text.requestFocus();
        text.requestFocusInWindow();
    }

    private void doCommandCompletion( String part ) {
        if ( nameCompletion == null )
            return;

        int i=part.length()-1;

        // Character.isJavaIdentifierPart()  How convenient for us!!
        while (
            i >= 0 &&
                ( Character.isJavaIdentifierPart(part.charAt(i))
                || part.charAt(i) == '.' )
        )
            i--;

        part = part.substring(i+1);

        if ( part.length() < 2 )  // reasonable completion length
            return;

        //System.out.println("completing part: "+part);

        // no completion
        String [] complete = nameCompletion.completeName(part);
        if ( complete.length == 0 ) {
            java.awt.Toolkit.getDefaultToolkit().beep();
            return;
        }

        // Found one completion (possibly what we already have)
        if ( complete.length == 1 && !complete.equals(part) ) {
            String append = complete[0].substring(part.length());
            append( append );
            return;
        }

        // Found ambiguous, show (some of) them

        String line = text.getText();
        if ( null == line )
            line = "";
        String command = line.substring( cmdStart );
        // Find prompt
        for(i=cmdStart; line.charAt(i) != '\n' && i > 0; i--);
        String prompt = line.substring( i+1, cmdStart );

        // Show ambiguous
        StringBuilder sb = new StringBuilder("\n");
        for( i=0; i<complete.length && i<SHOW_AMBIG_MAX; i++)
            sb.append( complete[i] +"\n" );
        if ( i == SHOW_AMBIG_MAX )
            sb.append("...\n");

        print( sb, Color.gray );
        print( prompt ); // print resets command start
        append( command ); // append does not reset command start
    }

    private void resetCommandStart() {
        cmdStart = textLength();
    }

    private void append(String string) {
        int slen = textLength();
        text.select(slen, slen);
        text.setSelectedText(string);
    }

    private String replaceRange(Object s, int start, int    end) {
        String st = s.toString();
        text.select(start, end);
        text.setSelectedText(st);
        //text.repaint();
        return st;
    }

    private void forceCaretMoveToEnd() {
        if (text.getCaretPosition() < cmdStart) {
            // move caret first!
            text.setCaretPosition(textLength());
        }
        text.repaint();
    }

    private void forceCaretMoveToStart() {
//        if (text.getCaretPosition() < cmdStart) {
            // move caret first!
//        }
        text.repaint();
    }


    private void enter() {
        String s = getCmd();

        if ( s.length() == 0 )  // special hack for empty return!
            s = ";\n";
        else {
            history.add( s );
            s = s +"\n";
        }

        append("\n");
        histLine = 0;
        acceptLine( s );
        text.repaint();
    }

    private String getCmd() {
        String s = "";
//        try {
            s = text.getText(cmdStart, textLength() - cmdStart);
/*        } catch (BadLocationException e) {
            // should not happen
            System.out.println("Internal JConsole Error: "+e);
        }
*/        return s;
    }

    private void historyUp() {
        if ( history.size() == 0 )
            return;
        if ( histLine == 0 )  // save current line
            startedLine = getCmd();
        if ( histLine < history.size() ) {
            histLine++;
            showHistoryLine();
        }
    }

    private void historyDown() {
        if ( histLine == 0 )
            return;

        histLine--;
        showHistoryLine();
    }

    private void showHistoryLine() {
        String showline;
        if ( histLine == 0 )
            showline = startedLine;
        else
            showline = history.get( history.size() - histLine );

        replaceRange( showline, cmdStart, textLength() );
        text.setCaretPosition(textLength());
        text.repaint();
    }

    String ZEROS = "000";

    private void acceptLine( String line )
    {
        // Patch to handle Unicode characters
        // Submitted by Daniel Leuck
        StringBuilder buf = new StringBuilder();
        int lineLength = line.length();
        for(int i=0; i<lineLength; i++) {
                char c = line.charAt(i);
                if(c>127) {
                    String val = Integer.toString(c, 16);
                    val=ZEROS.substring(0,4-val.length()) + val;
                    buf.append("\\u" + val);
                } else {
                    buf.append(c);
                }
        }
        line = buf.toString();
        // End unicode patch


        if (outPipe == null )
            print("Console internal error: cannot output ...", Color.red);
        else
            try {
                outPipe.write( line.getBytes(StandardCharsets.UTF_8) );
                outPipe.flush();
            } catch ( IOException e ) {
                outPipe = null;
                throw new RuntimeException("Console pipe broken...");
            }
        //text.repaint();
    }

    public void println(Object o) {
        print( String.valueOf(o) + "\n" );
        text.repaint();
    }

    public void print(final Object o) {
        invokeAndWait(new Runnable() {
            public void run() {
                append(String.valueOf(o));
                resetCommandStart();
                text.setCaretPosition(cmdStart);
            }
        });
    }

    /**
      * Prints "\\n" (i.e. newline)
      */
    public void println() {
        print("\n");
        text.repaint();
    }

    public void error( Object o ) {
        print( o, Color.red );
    }

    public void println(Icon icon) {
        print(icon);
        println();
        text.repaint();
    }

    public void print(final Icon icon) {
        if (icon==null)
            return;

        invokeAndWait(new Runnable() {
            public void run() {
//                text.insertIcon(icon);
                resetCommandStart();
                text.setCaretPosition(cmdStart);
            }
        });
    }

    public void print(Object s, Font font) {
        print(s, font, null);
    }

    public void print(Object s, Color color) {
        print(s, null, color);
    }

    public void print(final Object o, final Font font, final Color color) {
        invokeAndWait(new Runnable() {
            public void run() {
                AttributeSet old = getStyle();
                setStyle(font, color);
                append(String.valueOf(o));
                resetCommandStart();
                text.setCaretPosition(cmdStart);
                setStyle(old, true);
            }
        });
    }

    public void print(
        Object s,
        String fontFamilyName,
        int size,
        Color color
        ) {

        print(s,fontFamilyName,size,color,false,false,false);
    }

    public void print(
        final Object o,
        final String fontFamilyName,
        final int   size,
        final Color color,
        final boolean bold,
        final  boolean italic,
        final boolean underline
        )
    {
        invokeAndWait(new Runnable() {
            public void run() {
                AttributeSet old = getStyle();
                setStyle(fontFamilyName, size, color, bold, italic, underline);
                append(String.valueOf(o));
                resetCommandStart();
                text.setCaretPosition(cmdStart);
                setStyle(old, true);
            }
        });
    }

    private AttributeSet setStyle( Font font, Color color)
    {
        if (font!=null)
            return setStyle( font.getFamily(), font.getSize(), color,
                font.isBold(), font.isItalic(),
                StyleConstants.isUnderline(getStyle()) );
        else
            return setStyle(null,-1,color);
    }

    private AttributeSet setStyle (
        String fontFamilyName, int  size, Color color)
    {
        MutableAttributeSet attr = new SimpleAttributeSet();
        if (color!=null)
            StyleConstants.setForeground(attr, color);
        if (fontFamilyName!=null)
            StyleConstants.setFontFamily(attr, fontFamilyName);
        if (size!=-1)
            StyleConstants.setFontSize(attr, size);

        setStyle(attr);

        return getStyle();
    }

    private AttributeSet setStyle(
        String fontFamilyName,
        int size,
        Color color,
        boolean bold,
        boolean italic,
        boolean underline
        )
    {
        MutableAttributeSet attr = new SimpleAttributeSet();
        if (color!=null)
            StyleConstants.setForeground(attr, color);
        if (fontFamilyName!=null)
            StyleConstants.setFontFamily(attr, fontFamilyName);
        if (size!=-1)
            StyleConstants.setFontSize(attr, size);
        StyleConstants.setBold(attr, bold);
        StyleConstants.setItalic(attr, italic);
        StyleConstants.setUnderline(attr, underline);

        setStyle(attr);

        return getStyle();
    }

    private void setStyle(AttributeSet attributes) {
        setStyle(attributes, false);
    }

    private void setStyle(AttributeSet attributes, boolean overWrite) {
        //text.setCharacterAttributes(attributes, overWrite);
    }

    private AttributeSet getStyle() {
        return null;//text.getCharacterAttributes();
    }

    public void setFont( Font font ) {
        super.setFont( font );

        if ( text != null )
            text.setFont( font );
    }

    private void inPipeWatcher() throws IOException {
        byte [] ba = new byte [256]; // arbitrary blocking factor
        int read;
        while ( (read = inPipe.read(ba)) != -1 ) {
            print( new String(ba, 0, read, StandardCharsets.UTF_8) );
            //text.repaint();
        }

        println("Console: Input closed...");
    }

    public void run() {
        try {
            inPipeWatcher();
        } catch ( IOException e ) {
            print("Console: I/O Error: "+e+"\n", Color.red);
        }
    }

    public String toString() {
        return "BeanShell console";
    }

    // MouseListener Interface
    public void mouseClicked(MouseEvent event) {
    }

    public void mousePressed(MouseEvent event) {
        if (event.isPopupTrigger()) {
            menu.show(
                (Component)event.getSource(), event.getX(), event.getY());
        }
    }

    public void mouseReleased(MouseEvent event) {
        if (event.isPopupTrigger()) {
            menu.show((Component)event.getSource(), event.getX(),
            event.getY());
        }
        text.repaint();
    }

    public void mouseEntered(MouseEvent event) { }

    public void mouseExited(MouseEvent event) { }

    // property change
    public void propertyChange(PropertyChangeEvent event) {
        if (event.getPropertyName().equals("lookAndFeel")) {
            SwingUtilities.updateComponentTreeUI(menu);
        }
    }

    // handle cut, copy and paste
    public void actionPerformed(ActionEvent event) {
        String cmd = event.getActionCommand();
        if (cmd.equals(CUT)) {
            text.cut();
        } else if (cmd.equals(COPY)) {
            text.copy();
        } else if (cmd.equals(PASTE)) {
            text.paste();
        }
    }

    /**
     * If not in the event thread run via SwingUtilities.invokeAndWait()
     */
    private void invokeAndWait(Runnable run) {
        if(!SwingUtilities.isEventDispatchThread()) {
            try {
                SwingUtilities.invokeAndWait(run);
            } catch(Exception e) {
                // shouldn't happen
                e.printStackTrace();
            }
        } else {
            run.run();
        }
    }

    /**
        The overridden read method in this class will not throw "Broken pipe"
        IOExceptions;  It will simply wait for new writers and data.
        This is used by the JConsole internal read thread to allow writers
        in different (and in particular ephemeral) threads to write to the pipe.

        It also checks a little more frequently than the original read().

        Warning: read() will not even error on a read to an explicitly closed
        pipe (override closed to for that).
    */
    public static class BlockingPipedInputStream extends PipedInputStream
    {
        boolean closed;
        public BlockingPipedInputStream( PipedOutputStream pout )
            throws IOException
        {
            super(pout);
        }
        public synchronized int read() throws IOException {
            if ( closed )
                throw new IOException("stream closed");

            while (super.in < 0) {  // While no data */
                notifyAll();    // Notify any writers to wake up
                try {
                    wait(750);
                } catch ( InterruptedException e ) {
                    throw new InterruptedIOException();
                }
            }
            // This is what the superclass does.
            int ret = buffer[super.out++] & 0xFF;
            if (super.out >= buffer.length)
                super.out = 0;
            if (super.in == super.out)
                super.in = -1;  /* now empty */
            return ret;
        }
        public void close() throws IOException {
            closed = true;
            super.close();
        }
    }

    public void setNameCompletion( NameCompletion nc ) {
        this.nameCompletion = nc;
    }

    public void setWaitFeedback( boolean on ) {
        if ( on )
            setCursor( Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR) );
        else
            setCursor( Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR) );
    }

    private int textLength() { return text.getDocument().getLength(); }

}


