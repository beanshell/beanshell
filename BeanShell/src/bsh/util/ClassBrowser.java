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

// For string related utils
import bsh.BshClassManager;
import bsh.StringUtil;

/**
	A simple class browser for the BeanShell desktop.
*/
public class ClassBrowser extends JSplitPane
	implements ListSelectionListener, TreeSelectionListener {

	JFrame frame;
	JInternalFrame iframe;
	Hashtable packages=new Hashtable();
	JList plist, classlist, mlist, conslist;
	String selectedPackage;
	Class selectedClass;
	JTextArea methodLine;
	String [] packagesList, classesList;
	Method [] methodList;
	Constructor [] consList;
	JTree tree;

	public ClassBrowser() {
		super( VERTICAL_SPLIT, true );
	}

	void addClass( String classname ) {
		String [] sa = BshClassManager.splitClassname( classname );
		String packn = sa[0];
		String classn = sa[1];
		
		Vector pack=(Vector)packages.get(packn);
		if ( pack == null ) {
			pack = new Vector();
			packages.put(packn, pack);
		}
		pack.addElement( classn );
	}

	void addJar( String jarname ) throws IOException {
		ZipFile f=new ZipFile(jarname);
		Enumeration  e=f.entries();
		//Vector v=new Vector();
		while( e.hasMoreElements() ) {
			String name=((ZipEntry)e.nextElement()).getName();
			if ( name.endsWith(".class") && (name.indexOf('$')==-1) ) {
				//v.addElement(name);
				addClass( name );
			}
		}
	}

	String [] toSortedList ( Vector v ) {
		String [] sa = new String [v.size()];
		v.copyInto(sa);
		return StringUtil.bubbleSort(sa);
	}

	void setClist( String packagename ) {
		this.selectedPackage = packagename;
		Vector v=(Vector)packages.get( packagename );
		if ( v == null )
			return;

		classesList = toSortedList(v);
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
			setTree( null );
			return;
		}

		Class clas;
		try {
			selectedClass = Class.forName( selectedPackage+"."+classname );
		} catch ( Exception e ) { 
			System.out.println(e);
			return;
		}
		methodList = getPublicMethods( selectedClass.getDeclaredMethods() );
		mlist.setListData( parseMethods(methodList) );
		setTree( selectedClass );
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

	void setTree( Class clas ) {
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

	public void init() {
		String cp=System.getProperty("java.class.path");
		String [] paths=StringUtil.split(cp, File.pathSeparator);
		for ( int i=0; i<paths.length; i++) {
			String lcPath= paths[i].toLowerCase();
			if ( lcPath.endsWith(".jar") || lcPath.endsWith(".zip") )
			try {
				System.out.println("Adding classes: "+paths[i]);
				addJar( paths[i] );
			} catch ( IOException e ) { 
			}
		}

		// do we have the core classes?
		// try some standard locations
		if ( packages.get("java.lang") == null )
			try {
				addJar( System.getProperty("java.home")+"/lib/rt.jar"  );
			} catch ( IOException e ) {
				System.out.println("Can't find core classes....");
			}

		Vector v=new Vector();
		Enumeration e=packages.keys();
		while(e.hasMoreElements()) 
			v.addElement(e.nextElement());

		packagesList = toSortedList(v);
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
		setTree(null);
		p.add( "Center", tree );
		bottompanel.add("Center", p );

		// give it a preferred height
		bottompanel.setPreferredSize(new java.awt.Dimension(150,150));
		
		setTopComponent( sp );
		setBottomComponent( bottompanel );
	}

	public static void main( String [] args ) {
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
		String [] sa = BshClassManager.splitClassname( classname );
		String packn = sa[0];
		String classn = sa[1];

		Vector v =  (Vector)packages.get(packn);
		if ( v == null )
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
