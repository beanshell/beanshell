/*****************************************************************************
 *                                                                           *
 *  This file is part of the BeanShell Java Scripting distribution.          *
 *  Documentation and updates may be found at http://www.beanshell.org/      *
 *                                                                           *
 *  BeanShell is distributed under the terms of the LGPL:                    *
 *  GNU Library Public License http://www.gnu.org/copyleft/lgpl.html         *
 *                                                                           *
 *  Patrick Niemeyer (pat@pat.net)                                           *
 *  Author of Exploring Java, O'Reilly & Associates                          *
 *  http://www.pat.net/~pat/                                                 *
 *                                                                           *
 *****************************************************************************/

package bsh.util;

import java.util.*;
import java.util.zip.*;
import javax.swing.*;
import javax.swing.tree.*;
import javax.swing.event.*;
import java.io.*;
import java.awt.*;
import java.lang.reflect.*;
import java.util.List;

// For string related utils
import bsh.BshClassManager;
import bsh.classpath.BshClassPath;
import bsh.ClassPathException;
import bsh.StringUtil;
import bsh.ConsoleInterface;

/**
	A simple class browser for the BeanShell desktop.
*/
public class ClassBrowser extends JSplitPane
	implements ListSelectionListener, TreeSelectionListener {

	BshClassPath classPath;

	// GUI
	JFrame frame;
	JInternalFrame iframe;
	JList plist, classlist, mlist, conslist;
	JTextArea methodLine;
	JTree tree;
	// For JList models
	String [] packagesList;
	String [] classesList;
	Method [] methodList;
	Constructor [] consList;

	String selectedPackage;
	Class selectedClass;

	public ClassBrowser() {
		super( VERTICAL_SPLIT, true );
	}

	String [] toSortedStrings ( Collection c ) {
		List l = new ArrayList( c );
		String [] sa = (String[])(l.toArray( new String[0] ));
		return StringUtil.bubbleSort(sa);
	}

	void setClist( String packagename ) {
		this.selectedPackage = packagename;

		Set s = classPath.getClassesForPackage( packagename );
		if ( s == null )
			return;

		classesList = toSortedStrings(s);
		classlist.setListData( classesList );
		//setMlist( (String)classlist.getModel().getElementAt(0) );
	}

	String [] parseMethods( Method [] methods ) {
		String [] sa = new String [ methods.length ] ;
		for(int i=0; i< sa.length; i++)
			sa[i] = methods[i].getName();
		//return bubbleSort(sa);
		return sa;
	}

	Method [] getPublicMethods( Method [] methods ) {
		Vector v = new Vector();
		for(int i=0; i< methods.length; i++)
			if ( Modifier.isPublic(methods[i].getModifiers()) )
				v.addElement( methods[i] );

		Method [] ma = new Method [ v.size() ];
		v.copyInto( ma );
		return ma;
	}

	void setMlist( String classname ) {
		if ( classname == null ) {
			mlist.setListData( new Object [] { } );
			setConslist( null );
			setClassTree( null );
			return;
		}

		Class clas;
		try {
			selectedClass = BshClassManager.classForName( classname );
		} catch ( Exception e ) { 
			System.out.println(e);
			return;
		}
		methodList = getPublicMethods( selectedClass.getDeclaredMethods() );
		mlist.setListData( parseMethods(methodList) );
		setClassTree( selectedClass );
		setConslist( selectedClass );
	}

	void setConslist( Class clas ) {
		if ( clas == null ) {
			conslist.setListData( new Object [] { } );
			return;
		}

		consList = clas.getConstructors();
		conslist.setListData( consList );
	}

	void setMethodLine( Object method ) {
		methodLine.setText( method==null ? "" : method.toString() );
	}

	void setClassTree( Class clas ) {
		if ( clas == null ) {
			tree.setModel( null );
			return;
		}
			
		MutableTreeNode bottom = null, top = null;
		DefaultMutableTreeNode up;
		do {
			up= new DefaultMutableTreeNode( clas.toString() );
			if ( top != null )
				up.add( top );
			else
				bottom = up;
			top = up;
		} while ( (clas = clas.getSuperclass()) != null );
		tree.setModel( new DefaultTreeModel(top) );

		TreeNode tn = bottom.getParent();
		if ( tn != null ) {
			TreePath tp =  new TreePath (
				((DefaultTreeModel)tree.getModel()).getPathToRoot( tn ) );
			tree.expandPath( tp );
		}
	}

	JPanel labeledPane( JComponent comp, String label ) {
		JPanel jp = new JPanel( new BorderLayout() );
		jp.add( "Center", comp );
		jp.add( "North", new JLabel(label, SwingConstants.CENTER) );
		return jp;
	}

	public void init() throws ClassPathException 
	{
		classPath = BshClassManager.getClassManager().getClassPath();
		classPath.insureInitialized( 
			// get feedback on mapping...
			new ConsoleInterface() {
				public Reader getIn() { return null; }
				public PrintStream getOut() { return System.out; }
				public PrintStream getErr() { return System.err; }
				public void println( String s ) { System.out.println(s); }
				public void print( String s ) { System.out.print(s); }
				public void print( String s, Color color ) { print( s ); }
				public void error( String s ) { print( s ); }
			}
		);

		List l = classPath.getPackagesList();
		packagesList = toSortedStrings(l);

		plist=new JList( packagesList );
		plist.addListSelectionListener(this);

		classlist=new JList();
		classlist.addListSelectionListener(this);

		mlist = new JList();
		mlist.addListSelectionListener(this);

		conslist = new JList();
		conslist.addListSelectionListener(this);

		JSplitPane methodspane = new JSplitPane(
			JSplitPane.VERTICAL_SPLIT, true, 
			labeledPane(new JScrollPane(mlist), "Methods"),
			labeledPane(new JScrollPane(conslist), "Constructors"));

		JSplitPane sp = new JSplitPane( 
			JSplitPane.HORIZONTAL_SPLIT, true, 
			labeledPane(new JScrollPane(classlist), "Classes"),
			methodspane );
		sp = new JSplitPane( 
			JSplitPane.HORIZONTAL_SPLIT, true, 
				labeledPane(new JScrollPane(plist), "Packages"), sp);

		JPanel bottompanel = new JPanel( new BorderLayout() );
		methodLine = new JTextArea(1,60);
		methodLine.setEditable(false);
		methodLine.setLineWrap(true);
		methodLine.setWrapStyleWord(true);
		methodLine.setFont( new Font("Monospaced", Font.BOLD, 14) );
		methodLine.setMargin( new Insets(5,5,5,5) );
		methodLine.setBorder( BorderFactory.createRaisedBevelBorder() );
		bottompanel.add("North", methodLine);
		JPanel p = new JPanel( new BorderLayout() );
		tree = new JTree();
		tree.addTreeSelectionListener( this );
		tree.setBorder( BorderFactory.createRaisedBevelBorder() );
		setClassTree(null);
		p.add( "Center", tree );
		bottompanel.add("Center", p );

		// give it a preferred height
		bottompanel.setPreferredSize(new java.awt.Dimension(150,150));
		
		setTopComponent( sp );
		setBottomComponent( bottompanel );
	}

	public static void main( String [] args ) 
		throws Exception
	{
		ClassBrowser cb = new ClassBrowser();
		cb.init();

		JFrame f=new JFrame("BeanShell Class Browser v0.7");
		f.getContentPane().add( "Center", cb );
		cb.setFrame( f );
		f.pack();
		f.show();
	}

	public void setFrame( JFrame frame ) {
		this.frame = frame;
	}
	public void setFrame( JInternalFrame frame ) {
		this.iframe = frame;
	}

	public void valueChanged(TreeSelectionEvent e) {
		driveToClass( e.getPath().getLastPathComponent().toString() );
	}

	public void valueChanged(ListSelectionEvent e) {
		if ( e.getSource() == plist ) {
			String selectedPackage = (String)plist.getSelectedValue();
			setClist( selectedPackage );
		} else
		if ( e.getSource() == classlist ) {
			String classname = (String)classlist.getSelectedValue();
			setMlist( classname );
		} else
		if ( e.getSource() == mlist ) {
			int i = mlist.getSelectedIndex();
			if ( i == -1 )
				setMethodLine( null );
			else
				setMethodLine( methodList[i] );
		} else
		if ( e.getSource() == conslist ) {
			int i = conslist.getSelectedIndex();
			if ( i == -1 )
				setMethodLine( null );
			else
				setMethodLine( consList[i] );
		} 
	}

	// fully qualified classname
	public void driveToClass( String classname ) {
		String [] sa = BshClassPath.splitClassname( classname );
		String packn = sa[0];
		String classn = sa[1];

		// Do we have the package?
		if ( classPath.getClassesForPackage(packn) == null )
			return;

		boolean found = false;
		for(int i=0; i< packagesList.length; i++) {
			if ( packagesList[i].equals(packn) ) {
				plist.setSelectedIndex(i);
				plist.ensureIndexIsVisible(i);
				found = true;
				break;
			}
		}
		if ( !found )
			return;

		for(int i=0; i< classesList.length; i++) {
			if ( classesList[i].equals(classn) ) {
				classlist.setSelectedIndex(i);
				classlist.ensureIndexIsVisible(i);
				break;
			}
		}
	}

	public void toFront() {
		if ( frame != null )
			frame.toFront();		
		else
		if ( iframe != null )
			iframe.toFront();		
	}
}
