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

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.Vector;
import bsh.*;


/**
	Misc utilities for the bsh.util package.
	Nothing in the core language (bsh package) should depend on this.
	Note: that promise is currently broken... fix it.
*/
public class Util {

	public static ConsoleInterface makeConsole() {
		if ( bsh.Capabilities.haveSwing() )
			return new JConsole();
		else
			return new AWTConsole();
	}

	static Window splashScreen;
	/*
		This could live in the desktop script.
		However we'd like to get it on the screen as quickly as possible.
	*/
	public static void startSplashScreen() {
		Window win=new Window( new Frame() );
        win.pack();
        BshCanvas can=new BshCanvas();
        can.setSize( 351, 144 ); // why is this necessary?
        Toolkit tk=Toolkit.getDefaultToolkit();
        Dimension dim=tk.getScreenSize();
        win.setBounds( dim.width/2-351/2, dim.height/2-144/2, 351, 144 );
        win.add("Center", can);
        Image img=tk.getImage( 
			Interpreter.class.getResource("/bsh/util/lib/splash.gif") );
        MediaTracker mt=new MediaTracker(can);
        mt.addImage(img,0);
        try { mt.waitForAll(); } catch ( Exception e ) { }
        Graphics gr=can.getBufferedGraphics();
        gr.drawImage(img, 0, 0, can);
        win.show();
        win.toFront();
		splashScreen = win;
	}

	public static void endSplashScreen() {
		if ( splashScreen != null )
			splashScreen.dispose();
	}

}
