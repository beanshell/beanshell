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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.Insets;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JInternalFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTree;
import javax.swing.SwingConstants;
import javax.swing.border.MatteBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.MutableTreeNode;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;

// For string related utils
import bsh.BshClassManager;
import bsh.ClassPathException;
import bsh.StringUtil;
import bsh.classpath.BshClassPath;
import bsh.classpath.ClassManagerImpl;
import bsh.classpath.ClassPathListener;

/**
 * A simple class browser for the BeanShell desktop.
 */
public class ClassBrowser extends JSplitPane
        implements ListSelectionListener, ClassPathListener {

    /** The Constant serialVersionUID. */
    private static final long serialVersionUID = 1L;
    /** The class path. */
    BshClassPath classPath;
    /** The class manager. */
    BshClassManager classManager;
    /** The GUI frame. */
    JFrame frame;
    /** The iframe. */
    JInternalFrame iframe;
    /** The fieldlist. */
    JList classlist, conslist, mlist, fieldlist;
    /** The ptree. */
    PackageTree ptree;
    /** The method line. */
    JTextArea methodLine;
    /** The tree. */
    JTree tree;
    /** The packages list For JList models. */
    String[] packagesList;
    /** The classes list. */
    String[] classesList;
    /** The cons list. */
    Constructor[] consList;
    /** The method list. */
    Method[] methodList;
    /** The field list. */
    Field[] fieldList;
    /** The selected package. */
    String selectedPackage;
    /** The selected class. */
    Class selectedClass;
    /** The Constant LIGHT_BLUE. */
    private static final Color LIGHT_BLUE = new Color(245, 245, 255);

    /**
     * Instantiates a new class browser.
     */
    public ClassBrowser() {
        this(BshClassManager.createClassManager(null/* interpreter */));
    }

    /**
     * Instantiates a new class browser.
     *
     * @param classManager
     *            the class manager
     */
    public ClassBrowser(final BshClassManager classManager) {
        super(VERTICAL_SPLIT, true);
        this.classManager = classManager;
        this.setBorder(null);
        final javax.swing.plaf.SplitPaneUI ui = this.getUI();
        if (ui instanceof javax.swing.plaf.basic.BasicSplitPaneUI)
            ((javax.swing.plaf.basic.BasicSplitPaneUI) ui).getDivider()
                    .setBorder(null);
    }

    /**
     * To sorted strings.
     *
     * @param c
     *            the c
     * @return the string[]
     */
    String[] toSortedStrings(final Collection c) {
        final List l = new ArrayList(c);
        final String[] sa = (String[]) l.toArray(new String[0]);
        return StringUtil.bubbleSort(sa);
    }

    /**
     * Sets the clist.
     *
     * @param packagename
     *            the new clist
     */
    void setClist(final String packagename) {
        this.selectedPackage = packagename;
        Set set = this.classPath.getClassesForPackage(packagename);
        if (set == null)
            set = new HashSet();
        // remove inner classes and shorten class names
        final List list = new ArrayList();
        final Iterator it = set.iterator();
        while (it.hasNext()) {
            final String cname = (String) it.next();
            if (cname.indexOf("$") == -1)
                list.add(BshClassPath.splitClassname(cname)[1]);
        }
        this.classesList = this.toSortedStrings(list);
        this.classlist.setListData(this.classesList);
        // setMlist((String)classlist.getModel().getElementAt(0));
    }

    /**
     * Parses the constructors.
     *
     * @param constructors
     *            the constructors
     * @return the string[]
     */
    String[] parseConstructors(final Constructor[] constructors) {
        final String[] sa = new String[constructors.length];
        for (int i = 0; i < sa.length; i++) {
            final Constructor con = constructors[i];
            sa[i] = StringUtil.methodString(con.getName(),
                    con.getParameterTypes());
        }
        // return bubbleSort(sa);
        return sa;
    }

    /**
     * Parses the methods.
     *
     * @param methods
     *            the methods
     * @return the string[]
     */
    String[] parseMethods(final Method[] methods) {
        final String[] sa = new String[methods.length];
        for (int i = 0; i < sa.length; i++)
            sa[i] = StringUtil.methodString(methods[i].getName(),
                    methods[i].getParameterTypes());
        // return bubbleSort(sa);
        return sa;
    }

    /**
     * Parses the fields.
     *
     * @param fields
     *            the fields
     * @return the string[]
     */
    String[] parseFields(final Field[] fields) {
        final String[] sa = new String[fields.length];
        for (int i = 0; i < sa.length; i++) {
            final Field f = fields[i];
            sa[i] = f.getName();
        }
        return sa;
    }

    /**
     * Gets the public constructors.
     *
     * @param constructors
     *            the constructors
     * @return the public constructors
     */
    Constructor[] getPublicConstructors(final Constructor[] constructors) {
        final Vector v = new Vector();
        for (final Constructor constructor : constructors)
            if (Modifier.isPublic(constructor.getModifiers()))
                v.addElement(constructor);
        final Constructor[] ca = new Constructor[v.size()];
        v.copyInto(ca);
        return ca;
    }

    /**
     * Gets the public methods.
     *
     * @param methods
     *            the methods
     * @return the public methods
     */
    Method[] getPublicMethods(final Method[] methods) {
        final Vector v = new Vector();
        for (final Method method : methods)
            if (Modifier.isPublic(method.getModifiers()))
                v.addElement(method);
        final Method[] ma = new Method[v.size()];
        v.copyInto(ma);
        return ma;
    }

    /**
     * Gets the public fields.
     *
     * @param fields
     *            the fields
     * @return the public fields
     */
    Field[] getPublicFields(final Field[] fields) {
        final Vector v = new Vector();
        for (final Field field : fields)
            if (Modifier.isPublic(field.getModifiers()))
                v.addElement(field);
        final Field[] fa = new Field[v.size()];
        v.copyInto(fa);
        return fa;
    }

    /**
     * Sets the conslist.
     *
     * @param clas
     *            the new conslist
     */
    void setConslist(final Class clas) {
        if (clas == null) {
            this.conslist.setListData(new Object[] {});
            return;
        }
        this.consList = this
                .getPublicConstructors(clas.getDeclaredConstructors());
        this.conslist.setListData(this.parseConstructors(this.consList));
    }

    /**
     * Sets the mlist.
     *
     * @param classname
     *            the new mlist
     */
    void setMlist(final String classname) {
        if (classname == null) {
            this.mlist.setListData(new Object[] {});
            this.setConslist(null);
            this.setClassTree(null);
            return;
        }
        try {
            if (this.selectedPackage.equals("<unpackaged>"))
                this.selectedClass = this.classManager.classForName(classname);
            else
                this.selectedClass = this.classManager
                        .classForName(this.selectedPackage + "." + classname);
        } catch (final Exception e) {
            System.err.println(e);
            return;
        }
        if (this.selectedClass == null) {
            // not found?
            System.err.println("class not found: " + classname);
            return;
        }
        this.methodList = this
                .getPublicMethods(this.selectedClass.getDeclaredMethods());
        this.mlist.setListData(this.parseMethods(this.methodList));
        this.setClassTree(this.selectedClass);
        this.setConslist(this.selectedClass);
        this.setFieldList(this.selectedClass);
    }

    /**
     * Sets the field list.
     *
     * @param clas
     *            the new field list
     */
    void setFieldList(final Class clas) {
        if (clas == null) {
            this.fieldlist.setListData(new Object[] {});
            return;
        }
        this.fieldList = this.getPublicFields(clas.getDeclaredFields());
        this.fieldlist.setListData(this.parseFields(this.fieldList));
    }

    /**
     * Sets the method line.
     *
     * @param method
     *            the new method line
     */
    void setMethodLine(final Object method) {
        this.methodLine.setText(method == null ? "" : method.toString());
    }

    /**
     * Sets the class tree.
     *
     * @param clas
     *            the new class tree
     */
    void setClassTree(Class clas) {
        if (clas == null) {
            this.tree.setModel(null);
            return;
        }
        MutableTreeNode bottom = null, top = null;
        DefaultMutableTreeNode up;
        do {
            up = new DefaultMutableTreeNode(clas.toString());
            if (top != null)
                up.add(top);
            else
                bottom = up;
            top = up;
        }
        while ((clas = clas.getSuperclass()) != null);
        this.tree.setModel(new DefaultTreeModel(top));
        final TreeNode tn = bottom.getParent();
        if (tn != null) {
            final TreePath tp = new TreePath(
                    ((DefaultTreeModel) this.tree.getModel())
                            .getPathToRoot(tn));
            this.tree.expandPath(tp);
        }
    }

    /**
     * Labeled pane.
     *
     * @param comp
     *            the comp
     * @param label
     *            the label
     * @return the j panel
     */
    JPanel labeledPane(final JComponent comp, final String label) {
        final JPanel jp = new JPanel(new BorderLayout());
        jp.add("Center", comp);
        jp.add("North", new JLabel(label, SwingConstants.CENTER));
        return jp;
    }

    /**
     * Inits the.
     *
     * @throws ClassPathException
     *             the class path exception
     */
    public void init() throws ClassPathException {
        // Currently we have to cast because BshClassPath is not known by
        // the core.
        this.classPath = ((ClassManagerImpl) this.classManager).getClassPath();
        // maybe add MappingFeedbackListener here... or let desktop if it has
        /*
         * classPath.insureInitialized(null
         * // get feedback on mapping...
         * new ConsoleInterface() {
         * public Reader getIn() { return null; }
         * public PrintStream getOut() { return System.out; }
         * public PrintStream getErr() { return System.err; }
         * public void println(String s) { System.out.println(s); }
         * public void print(String s) { System.out.print(s); }
         * public void print(String s, Color color) { print(s); }
         * public void error(String s) { print(s); }
         * }
         * );
         */
        this.classPath.addListener(this);
        final Set pset = this.classPath.getPackagesSet();
        this.ptree = new PackageTree(pset);
        this.ptree.addTreeSelectionListener(new TreeSelectionListener() {

            public void valueChanged(final TreeSelectionEvent e) {
                final TreePath tp = e.getPath();
                final Object[] oa = tp.getPath();
                final StringBuffer selectedPackage = new StringBuffer();
                for (int i = 1; i < oa.length; i++) {
                    selectedPackage.append(oa[i].toString());
                    if (i + 1 < oa.length)
                        selectedPackage.append(".");
                }
                ClassBrowser.this.setClist(selectedPackage.toString());
            }
        });
        this.classlist = new JList();
        this.classlist.setBackground(LIGHT_BLUE);
        this.classlist.addListSelectionListener(this);
        this.conslist = new JList();
        this.conslist.addListSelectionListener(this);
        this.mlist = new JList();
        this.mlist.setBackground(LIGHT_BLUE);
        this.mlist.addListSelectionListener(this);
        this.fieldlist = new JList();
        this.fieldlist.addListSelectionListener(this);
        final JSplitPane methodConsPane = this.splitPane(
                JSplitPane.VERTICAL_SPLIT, true,
                this.labeledPane(new JScrollPane(this.conslist),
                        "Constructors"),
                this.labeledPane(new JScrollPane(this.mlist), "Methods"));
        final JSplitPane rightPane = this.splitPane(JSplitPane.VERTICAL_SPLIT,
                true, methodConsPane,
                this.labeledPane(new JScrollPane(this.fieldlist), "Fields"));
        JSplitPane sp = this.splitPane(JSplitPane.HORIZONTAL_SPLIT, true,
                this.labeledPane(new JScrollPane(this.classlist), "Classes"),
                rightPane);
        sp = this.splitPane(JSplitPane.HORIZONTAL_SPLIT, true,
                this.labeledPane(new JScrollPane(this.ptree), "Packages"), sp);
        final JPanel bottompanel = new JPanel(new BorderLayout());
        this.methodLine = new JTextArea(1, 60);
        this.methodLine.setBackground(LIGHT_BLUE);
        this.methodLine.setEditable(false);
        this.methodLine.setLineWrap(true);
        this.methodLine.setWrapStyleWord(true);
        this.methodLine.setFont(new Font("Monospaced", Font.BOLD, 14));
        this.methodLine.setMargin(new Insets(5, 5, 5, 5));
        this.methodLine.setBorder(
                new MatteBorder(1, 0, 1, 0, LIGHT_BLUE.darker().darker()));
        bottompanel.add("North", this.methodLine);
        final JPanel p = new JPanel(new BorderLayout());
        this.tree = new JTree();
        this.tree.addTreeSelectionListener(new TreeSelectionListener() {

            public void valueChanged(final TreeSelectionEvent e) {
                ClassBrowser.this.driveToClass(
                        e.getPath().getLastPathComponent().toString());
            }
        });
        this.tree.setBorder(BorderFactory.createRaisedBevelBorder());
        this.setClassTree(null);
        p.add("Center", this.tree);
        bottompanel.add("Center", p);
        // give it a preferred height
        bottompanel.setPreferredSize(new java.awt.Dimension(150, 150));
        this.setTopComponent(sp);
        this.setBottomComponent(bottompanel);
    }

    /**
     * Split pane.
     *
     * @param orientation
     *            the orientation
     * @param redraw
     *            the redraw
     * @param c1
     *            the c 1
     * @param c2
     *            the c 2
     * @return the j split pane
     */
    private JSplitPane splitPane(final int orientation, final boolean redraw,
            final JComponent c1, final JComponent c2) {
        final JSplitPane sp = new JSplitPane(orientation, redraw, c1, c2);
        sp.setBorder(null);
        final javax.swing.plaf.SplitPaneUI ui = sp.getUI();
        if (ui instanceof javax.swing.plaf.basic.BasicSplitPaneUI)
            ((javax.swing.plaf.basic.BasicSplitPaneUI) ui).getDivider()
                    .setBorder(null);
        return sp;
    }

    /**
     * The main method.
     *
     * @param args
     *            the arguments
     * @throws Exception
     *             the exception
     */
    public static void main(final String[] args) throws Exception {
        final ClassBrowser cb = new ClassBrowser();
        cb.init();
        final JFrame f = new JFrame("BeanShell Class Browser v1.0");
        f.getContentPane().add("Center", cb);
        cb.setFrame(f);
        f.pack();
        f.setVisible(true);
    }

    /**
     * Sets the frame.
     *
     * @param frame
     *            the new frame
     */
    public void setFrame(final JFrame frame) {
        this.frame = frame;
    }

    /**
     * Sets the frame.
     *
     * @param frame
     *            the new frame
     */
    public void setFrame(final JInternalFrame frame) {
        this.iframe = frame;
    }

    /** {@inheritDoc} */
    public void valueChanged(final ListSelectionEvent e) {
        if (e.getSource() == this.classlist) {
            final String classname = (String) this.classlist.getSelectedValue();
            this.setMlist(classname);
            // hack
            // show the class source in the "method" line...
            String methodLineString;
            if (classname == null)
                methodLineString = "Package: " + this.selectedPackage;
            else {
                final String fullClassName = this.selectedPackage
                        .equals("<unpackaged>") ? classname
                                : this.selectedPackage + "." + classname;
                methodLineString = fullClassName + " (from "
                        + this.classPath.getClassSource(fullClassName) + ")";
            }
            this.setMethodLine(methodLineString);
        } else if (e.getSource() == this.mlist) {
            final int i = this.mlist.getSelectedIndex();
            if (i == -1)
                this.setMethodLine(null);
            else
                this.setMethodLine(this.methodList[i]);
        } else if (e.getSource() == this.conslist) {
            final int i = this.conslist.getSelectedIndex();
            if (i == -1)
                this.setMethodLine(null);
            else
                this.setMethodLine(this.consList[i]);
        } else if (e.getSource() == this.fieldlist) {
            final int i = this.fieldlist.getSelectedIndex();
            if (i == -1)
                this.setMethodLine(null);
            else
                this.setMethodLine(this.fieldList[i]);
        }
    }

    /**
     * Drive to  fully qualified classname.
     *
     * @param classname
     *            the classname
     */
    public void driveToClass(final String classname) {
        final String[] sa = BshClassPath.splitClassname(classname);
        final String packn = sa[0];
        final String classn = sa[1];
        // Do we have the package?
        if (this.classPath.getClassesForPackage(packn).size() == 0)
            return;
        this.ptree.setSelectedPackage(packn);
        for (int i = 0; i < this.classesList.length; i++)
            if (this.classesList[i].equals(classn)) {
                this.classlist.setSelectedIndex(i);
                this.classlist.ensureIndexIsVisible(i);
                break;
            }
    }

    /**
     * To front.
     */
    public void toFront() {
        if (this.frame != null)
            this.frame.toFront();
        else if (this.iframe != null)
            this.iframe.toFront();
    }

    /**
     * The Class PackageTree.
     */
    class PackageTree extends JTree {

        /** The Constant serialVersionUID. */
        private static final long serialVersionUID = 1L;
        /** The root. */
        TreeNode root;
        /** The tree model. */
        DefaultTreeModel treeModel;
        /** The node for package. */
        Map nodeForPackage = new HashMap();

        /**
         * Instantiates a new package tree.
         *
         * @param packages
         *            the packages
         */
        PackageTree(final Collection packages) {
            this.setPackages(packages);
            this.setRootVisible(false);
            this.setShowsRootHandles(true);
            this.setExpandsSelectedPaths(true);
            // open top level paths
            /*
             * Enumeration e1=root.children();
             * while(e1.hasMoreElements()) {
             * TreePath tp = new TreePath(
             * treeModel.getPathToRoot((TreeNode)e1.nextElement()));
             * expandPath(tp);
             * }
             */
        }

        /**
         * Sets the packages.
         *
         * @param packages
         *            the new packages
         */
        public void setPackages(final Collection packages) {
            this.treeModel = this.makeTreeModel(packages);
            this.setModel(this.treeModel);
        }

        /**
         * Make tree model.
         *
         * @param packages
         *            the packages
         * @return the default tree model
         */
        DefaultTreeModel makeTreeModel(final Collection packages) {
            final Map packageTree = new HashMap();
            final Iterator it = packages.iterator();
            while (it.hasNext()) {
                final String pack = (String) it.next();
                final String[] sa = StringUtil.split(pack, ".");
                Map level = packageTree;
                for (final String name : sa) {
                    Map map = (Map) level.get(name);
                    if (map == null) {
                        map = new HashMap();
                        level.put(name, map);
                    }
                    level = map;
                }
            }
            this.root = this.makeNode(packageTree, "root");
            this.mapNodes(this.root);
            return new DefaultTreeModel(this.root);
        }

        /**
         * Make node.
         *
         * @param map
         *            the map
         * @param nodeName
         *            the node name
         * @return the mutable tree node
         */
        MutableTreeNode makeNode(final Map map, final String nodeName) {
            final DefaultMutableTreeNode root = new DefaultMutableTreeNode(
                    nodeName);
            final Iterator it = map.keySet().iterator();
            while (it.hasNext()) {
                final String name = (String) it.next();
                final Map val = (Map) map.get(name);
                if (val.size() == 0) {
                    final DefaultMutableTreeNode leaf = new DefaultMutableTreeNode(
                            name);
                    root.add(leaf);
                } else {
                    final MutableTreeNode node = this.makeNode(val, name);
                    root.add(node);
                }
            }
            return root;
        }

        /**
         * Map out the location of the nodes by package name.
         * Seems like we should be able to do this while we build above...
         * I'm tired... just going to do this.
         *
         * @param node
         *            the node
         */
        void mapNodes(final TreeNode node) {
            this.addNodeMap(node);
            final Enumeration e = node.children();
            while (e.hasMoreElements()) {
                final TreeNode tn = (TreeNode) e.nextElement();
                this.mapNodes(tn);
            }
        }

        /**
         * map a single node up to the root.
         *
         * @param node
         *            the node
         */
        void addNodeMap(final TreeNode node) {
            final StringBuffer sb = new StringBuffer();
            TreeNode tn = node;
            while (tn != this.root) {
                sb.insert(0, tn.toString());
                if (tn.getParent() != this.root)
                    sb.insert(0, ".");
                tn = tn.getParent();
            }
            final String pack = sb.toString();
            this.nodeForPackage.put(pack, node);
        }

        /**
         * Sets the selected package.
         *
         * @param pack
         *            the new selected package
         */
        void setSelectedPackage(final String pack) {
            final DefaultMutableTreeNode node =
                    (DefaultMutableTreeNode) this.nodeForPackage.get(pack);
            if (node == null)
                return;
            final TreePath tp = new TreePath(
                    this.treeModel.getPathToRoot(node));
            this.setSelectionPath(tp);
            ClassBrowser.this.setClist(pack);
            this.scrollPathToVisible(tp);
        }
    }

    /** {@inheritDoc} */
    public void classPathChanged() {
        final Set pset = this.classPath.getPackagesSet();
        this.ptree.setPackages(pset);
        this.setClist(null);
    }
}
