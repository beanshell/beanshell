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

import java.applet.Applet;
import java.awt.*;
import java.io.*;
import java.net.*;
import bsh.Console;

/**
	A lightweight console applet for remote display of a Beanshel session.
*/
public class RemoteBshApplet extends Applet
{
	public void init() {
		setLayout(new BorderLayout());

		OutputStream out;
		InputStream in;

		try {
			URL base = getDocumentBase();

			// connect to session server on port (httpd + 1)
			Socket s = new Socket(base.getHost(), base.getPort() + 1);
			out = s.getOutputStream();
			in = s.getInputStream();
		} catch(IOException e) {
			add("Center", new Label("Remote Connection Failed", Label.CENTER));
			return;
		}

		Component console;
		if ( bsh.NameSpace.haveSwing() )
			console = new javax.swing.JScrollPane(new JConsole(in, out));
		else
			console = new AWTConsole(in, out);

		add("Center", console);
	}
}

