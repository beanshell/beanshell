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
import javax.swing.*;
import bsh.*;

/**
	Scriptable Canvas with buffered graphics.

	Provides a Component that:
	1) delegates calls to paint() to a bsh method called paint() 
		in a specific NameSpace.
	2) provides a simple buffered image maintained by built in paint() that 
		is useful for simple immediate procedural rendering from scripts...  

*/
public class BshCanvas extends JComponent {
	This ths;
	Image imageBuffer;

	public BshCanvas () { }

	public BshCanvas ( This ths ) {
		this.ths = ths;
	}

	public void paintComponent( Graphics g ) {
		// copy buffered image
		if ( imageBuffer != null )
			g.drawImage(imageBuffer, 0,0, this);

		// Delegate call to scripted paint() method
		if ( ths != null ) {
			try {
				ths.invokeMethod( "paint", new Object[] { g } );
			} catch(EvalError e) {
				Interpreter.debug(
					"BshCanvas: method invocation error:" + e);
			}
		}
	}

	/**
		Get a buffered (persistent) image for drawing on this component
	*/
	public Graphics getBufferedGraphics() {
		Dimension dim = getSize();
		imageBuffer = createImage( dim.width, dim.height );
		return imageBuffer.getGraphics();
	}

	public void setBounds( int x, int y, int width, int height ) {
		setPreferredSize( new Dimension(width, height) );
		setMinimumSize( new Dimension(width, height) );
		super.setBounds( x, y, width, height );
	}

}

