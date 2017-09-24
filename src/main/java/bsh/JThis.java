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
package bsh;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.ContainerEvent;
import java.awt.event.ContainerListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.TextEvent;
import java.awt.event.TextListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;
import javax.swing.event.CellEditorListener;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.event.InternalFrameEvent;
import javax.swing.event.InternalFrameListener;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.MenuDragMouseEvent;
import javax.swing.event.MenuDragMouseListener;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuKeyEvent;
import javax.swing.event.MenuKeyListener;
import javax.swing.event.MenuListener;
import javax.swing.event.MouseInputListener;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.event.TableColumnModelEvent;
import javax.swing.event.TableColumnModelListener;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.event.UndoableEditEvent;
import javax.swing.event.UndoableEditListener;

/**
 * JThis is a dynamically loaded extension which extends This and adds
 * explicit support for AWT and JFC events, etc. This is a backwards
 * compatibility measure for JDK 1.2. With 1.3+ there is a general
 * reflection proxy mechanism that allows the base This to implement
 * arbitrary interfaces.
 *
 * The NameSpace getThis() method will produce instances of JThis if
 * the java version is prior to 1.3 and swing is available... (e.g. 1.2
 * or 1.1 + swing installed)
 *
 * Users of 1.1 without swing will have minimal interface support (just run()).
 *
 * Bsh doesn't run on 1.02 and below because there is no reflection!
 *
 * Note: This module relies on features of Swing and will only compile
 * with JDK1.2 or JDK1.1 + the swing package. For other environments simply
 * do not compile this class.
 */
class JThis extends This implements
        // All core AWT listeners
        ActionListener, AdjustmentListener, ComponentListener,
        ContainerListener, FocusListener, ItemListener, KeyListener,
        MouseListener, MouseMotionListener, TextListener, WindowListener,
        PropertyChangeListener,
        // All listeners in javax.swing.event as of Swing 1.1
        AncestorListener, CaretListener, CellEditorListener, ChangeListener,
        DocumentListener, HyperlinkListener, InternalFrameListener,
        ListDataListener, ListSelectionListener, MenuDragMouseListener,
        MenuKeyListener, MenuListener, MouseInputListener, PopupMenuListener,
        TableColumnModelListener, TableModelListener, TreeExpansionListener,
        TreeModelListener, TreeSelectionListener, TreeWillExpandListener,
        UndoableEditListener {

    /** The Constant serialVersionUID. */
    private static final long serialVersionUID = 1L;

    /**
     * Instantiates a new j this.
     *
     * @param namespace
     *            the namespace
     * @param declaringInterp
     *            the declaring interp
     */
    JThis(final NameSpace namespace, final Interpreter declaringInterp) {
        super(namespace, declaringInterp);
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        return "'this' reference (JThis) to Bsh object: "
                + this.namespace.getName();
    }

    /**
     * Event.
     *
     * @param name
     *            the name
     * @param event
     *            the event
     */
    void event(final String name, final Object event) {
        final CallStack callstack = new CallStack(this.namespace);
        BshMethod method = null;
        // handleEvent gets all events
        try {
            method = this.namespace.getMethod("handleEvent",
                    new Class[] {null});
        } catch (final UtilEvalError e) {/* squeltch */ }
        if (method != null)
            try {
                method.invoke(new Object[] {event}, this.declaringInterpreter,
                        callstack, null);
            } catch (final EvalError e) {
                this.declaringInterpreter.error(
                        "local event hander method invocation error:" + e);
            }
        // send to specific event handler
        try {
            method = this.namespace.getMethod(name, new Class[] {null});
        } catch (final UtilEvalError e) { /* squeltch */ }
        if (method != null)
            try {
                method.invoke(new Object[] {event}, this.declaringInterpreter,
                        callstack, null);
            } catch (final EvalError e) {
                this.declaringInterpreter.error(
                        "local event hander method invocation error:" + e);
            }
    }

    // Listener interfaces
    /** {@inheritDoc} */
    public void ancestorAdded(final AncestorEvent e) {
        this.event("ancestorAdded", e);
    }

    /** {@inheritDoc} */
    public void ancestorRemoved(final AncestorEvent e) {
        this.event("ancestorRemoved", e);
    }

    /** {@inheritDoc} */
    public void ancestorMoved(final AncestorEvent e) {
        this.event("ancestorMoved", e);
    }

    /** {@inheritDoc} */
    public void caretUpdate(final CaretEvent e) {
        this.event("caretUpdate", e);
    }

    /** {@inheritDoc} */
    public void editingStopped(final ChangeEvent e) {
        this.event("editingStopped", e);
    }

    /** {@inheritDoc} */
    public void editingCanceled(final ChangeEvent e) {
        this.event("editingCanceled", e);
    }

    /** {@inheritDoc} */
    public void stateChanged(final ChangeEvent e) {
        this.event("stateChanged", e);
    }

    /** {@inheritDoc} */
    public void insertUpdate(final DocumentEvent e) {
        this.event("insertUpdate", e);
    }

    /** {@inheritDoc} */
    public void removeUpdate(final DocumentEvent e) {
        this.event("removeUpdate", e);
    }

    /** {@inheritDoc} */
    public void changedUpdate(final DocumentEvent e) {
        this.event("changedUpdate", e);
    }

    /** {@inheritDoc} */
    public void hyperlinkUpdate(final HyperlinkEvent e) {
        this.event("internalFrameOpened", e);
    }

    /** {@inheritDoc} */
    public void internalFrameOpened(final InternalFrameEvent e) {
        this.event("internalFrameOpened", e);
    }

    /** {@inheritDoc} */
    public void internalFrameClosing(final InternalFrameEvent e) {
        this.event("internalFrameClosing", e);
    }

    /** {@inheritDoc} */
    public void internalFrameClosed(final InternalFrameEvent e) {
        this.event("internalFrameClosed", e);
    }

    /** {@inheritDoc} */
    public void internalFrameIconified(final InternalFrameEvent e) {
        this.event("internalFrameIconified", e);
    }

    /** {@inheritDoc} */
    public void internalFrameDeiconified(final InternalFrameEvent e) {
        this.event("internalFrameDeiconified", e);
    }

    /** {@inheritDoc} */
    public void internalFrameActivated(final InternalFrameEvent e) {
        this.event("internalFrameActivated", e);
    }

    /** {@inheritDoc} */
    public void internalFrameDeactivated(final InternalFrameEvent e) {
        this.event("internalFrameDeactivated", e);
    }

    /** {@inheritDoc} */
    public void intervalAdded(final ListDataEvent e) {
        this.event("intervalAdded", e);
    }

    /** {@inheritDoc} */
    public void intervalRemoved(final ListDataEvent e) {
        this.event("intervalRemoved", e);
    }

    /** {@inheritDoc} */
    public void contentsChanged(final ListDataEvent e) {
        this.event("contentsChanged", e);
    }

    /** {@inheritDoc} */
    public void valueChanged(final ListSelectionEvent e) {
        this.event("valueChanged", e);
    }

    /** {@inheritDoc} */
    public void menuDragMouseEntered(final MenuDragMouseEvent e) {
        this.event("menuDragMouseEntered", e);
    }

    /** {@inheritDoc} */
    public void menuDragMouseExited(final MenuDragMouseEvent e) {
        this.event("menuDragMouseExited", e);
    }

    /** {@inheritDoc} */
    public void menuDragMouseDragged(final MenuDragMouseEvent e) {
        this.event("menuDragMouseDragged", e);
    }

    /** {@inheritDoc} */
    public void menuDragMouseReleased(final MenuDragMouseEvent e) {
        this.event("menuDragMouseReleased", e);
    }

    /** {@inheritDoc} */
    public void menuKeyTyped(final MenuKeyEvent e) {
        this.event("menuKeyTyped", e);
    }

    /** {@inheritDoc} */
    public void menuKeyPressed(final MenuKeyEvent e) {
        this.event("menuKeyPressed", e);
    }

    /** {@inheritDoc} */
    public void menuKeyReleased(final MenuKeyEvent e) {
        this.event("menuKeyReleased", e);
    }

    /** {@inheritDoc} */
    public void menuSelected(final MenuEvent e) {
        this.event("menuSelected", e);
    }

    /** {@inheritDoc} */
    public void menuDeselected(final MenuEvent e) {
        this.event("menuDeselected", e);
    }

    /** {@inheritDoc} */
    public void menuCanceled(final MenuEvent e) {
        this.event("menuCanceled", e);
    }

    /** {@inheritDoc} */
    public void popupMenuWillBecomeVisible(final PopupMenuEvent e) {
        this.event("popupMenuWillBecomeVisible", e);
    }

    /** {@inheritDoc} */
    public void popupMenuWillBecomeInvisible(final PopupMenuEvent e) {
        this.event("popupMenuWillBecomeInvisible", e);
    }

    /** {@inheritDoc} */
    public void popupMenuCanceled(final PopupMenuEvent e) {
        this.event("popupMenuCanceled", e);
    }

    /** {@inheritDoc} */
    public void columnAdded(final TableColumnModelEvent e) {
        this.event("columnAdded", e);
    }

    /** {@inheritDoc} */
    public void columnRemoved(final TableColumnModelEvent e) {
        this.event("columnRemoved", e);
    }

    /** {@inheritDoc} */
    public void columnMoved(final TableColumnModelEvent e) {
        this.event("columnMoved", e);
    }

    /** {@inheritDoc} */
    public void columnMarginChanged(final ChangeEvent e) {
        this.event("columnMarginChanged", e);
    }

    /** {@inheritDoc} */
    public void columnSelectionChanged(final ListSelectionEvent e) {
        this.event("columnSelectionChanged", e);
    }

    /** {@inheritDoc} */
    public void tableChanged(final TableModelEvent e) {
        this.event("tableChanged", e);
    }

    /** {@inheritDoc} */
    public void treeExpanded(final TreeExpansionEvent e) {
        this.event("treeExpanded", e);
    }

    /** {@inheritDoc} */
    public void treeCollapsed(final TreeExpansionEvent e) {
        this.event("treeCollapsed", e);
    }

    /** {@inheritDoc} */
    public void treeNodesChanged(final TreeModelEvent e) {
        this.event("treeNodesChanged", e);
    }

    /** {@inheritDoc} */
    public void treeNodesInserted(final TreeModelEvent e) {
        this.event("treeNodesInserted", e);
    }

    /** {@inheritDoc} */
    public void treeNodesRemoved(final TreeModelEvent e) {
        this.event("treeNodesRemoved", e);
    }

    /** {@inheritDoc} */
    public void treeStructureChanged(final TreeModelEvent e) {
        this.event("treeStructureChanged", e);
    }

    /** {@inheritDoc} */
    public void valueChanged(final TreeSelectionEvent e) {
        this.event("valueChanged", e);
    }

    /** {@inheritDoc} */
    public void treeWillExpand(final TreeExpansionEvent e) {
        this.event("treeWillExpand", e);
    }

    /** {@inheritDoc} */
    public void treeWillCollapse(final TreeExpansionEvent e) {
        this.event("treeWillCollapse", e);
    }

    /** {@inheritDoc} */
    public void undoableEditHappened(final UndoableEditEvent e) {
        this.event("undoableEditHappened", e);
    }

    // Listener interfaces
    /** {@inheritDoc} */
    public void actionPerformed(final ActionEvent e) {
        this.event("actionPerformed", e);
    }

    /** {@inheritDoc} */
    public void adjustmentValueChanged(final AdjustmentEvent e) {
        this.event("adjustmentValueChanged", e);
    }

    /** {@inheritDoc} */
    public void componentResized(final ComponentEvent e) {
        this.event("componentResized", e);
    }

    /** {@inheritDoc} */
    public void componentMoved(final ComponentEvent e) {
        this.event("componentMoved", e);
    }

    /** {@inheritDoc} */
    public void componentShown(final ComponentEvent e) {
        this.event("componentShown", e);
    }

    /** {@inheritDoc} */
    public void componentHidden(final ComponentEvent e) {
        this.event("componentHidden", e);
    }

    /** {@inheritDoc} */
    public void componentAdded(final ContainerEvent e) {
        this.event("componentAdded", e);
    }

    /** {@inheritDoc} */
    public void componentRemoved(final ContainerEvent e) {
        this.event("componentRemoved", e);
    }

    /** {@inheritDoc} */
    public void focusGained(final FocusEvent e) {
        this.event("focusGained", e);
    }

    /** {@inheritDoc} */
    public void focusLost(final FocusEvent e) {
        this.event("focusLost", e);
    }

    /** {@inheritDoc} */
    public void itemStateChanged(final ItemEvent e) {
        this.event("itemStateChanged", e);
    }

    /** {@inheritDoc} */
    public void keyTyped(final KeyEvent e) {
        this.event("keyTyped", e);
    }

    /** {@inheritDoc} */
    public void keyPressed(final KeyEvent e) {
        this.event("keyPressed", e);
    }

    /** {@inheritDoc} */
    public void keyReleased(final KeyEvent e) {
        this.event("keyReleased", e);
    }

    /** {@inheritDoc} */
    public void mouseClicked(final MouseEvent e) {
        this.event("mouseClicked", e);
    }

    /** {@inheritDoc} */
    public void mousePressed(final MouseEvent e) {
        this.event("mousePressed", e);
    }

    /** {@inheritDoc} */
    public void mouseReleased(final MouseEvent e) {
        this.event("mouseReleased", e);
    }

    /** {@inheritDoc} */
    public void mouseEntered(final MouseEvent e) {
        this.event("mouseEntered", e);
    }

    /** {@inheritDoc} */
    public void mouseExited(final MouseEvent e) {
        this.event("mouseExited", e);
    }

    /** {@inheritDoc} */
    public void mouseDragged(final MouseEvent e) {
        this.event("mouseDragged", e);
    }

    /** {@inheritDoc} */
    public void mouseMoved(final MouseEvent e) {
        this.event("mouseMoved", e);
    }

    /** {@inheritDoc} */
    public void textValueChanged(final TextEvent e) {
        this.event("textValueChanged", e);
    }

    /** {@inheritDoc} */
    public void windowOpened(final WindowEvent e) {
        this.event("windowOpened", e);
    }

    /** {@inheritDoc} */
    public void windowClosing(final WindowEvent e) {
        this.event("windowClosing", e);
    }

    /** {@inheritDoc} */
    public void windowClosed(final WindowEvent e) {
        this.event("windowClosed", e);
    }

    /** {@inheritDoc} */
    public void windowIconified(final WindowEvent e) {
        this.event("windowIconified", e);
    }

    /** {@inheritDoc} */
    public void windowDeiconified(final WindowEvent e) {
        this.event("windowDeiconified", e);
    }

    /** {@inheritDoc} */
    public void windowActivated(final WindowEvent e) {
        this.event("windowActivated", e);
    }

    /** {@inheritDoc} */
    public void windowDeactivated(final WindowEvent e) {
        this.event("windowDeactivated", e);
    }

    /** {@inheritDoc} */
    public void propertyChange(final PropertyChangeEvent e) {
        this.event("propertyChange", e);
    }

    /**
     * Vetoable change.
     *
     * @param e
     *            the e
     */
    public void vetoableChange(final PropertyChangeEvent e) {
        this.event("vetoableChange", e);
    }

    /**
     * Image update.
     *
     * @param img
     *            the img
     * @param infoflags
     *            the infoflags
     * @param x
     *            the x
     * @param y
     *            the y
     * @param width
     *            the width
     * @param height
     *            the height
     * @return true, if successful
     */
    public boolean imageUpdate(final java.awt.Image img, final int infoflags,
            final int x, final int y, final int width, final int height) {
        BshMethod method = null;
        try {
            method = this.namespace.getMethod("imageUpdate",
                    new Class[] {null, null, null, null, null, null});
        } catch (final UtilEvalError e) {/* squeltch */ }
        if (method != null)
            try {
                final CallStack callstack = new CallStack(this.namespace);
                method.invoke(
                        new Object[] {img, new Primitive(infoflags),
                                new Primitive(x), new Primitive(y),
                                new Primitive(width), new Primitive(height)},
                        this.declaringInterpreter, callstack, null);
            } catch (final EvalError e) {
                this.declaringInterpreter.error(
                        "local event handler imageUpdate: method invocation error:"
                                + e);
            }
        return true;
    }
}
