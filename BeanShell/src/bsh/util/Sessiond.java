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

import java.io.InputStream;
import java.io.PrintStream;
import java.io.IOException;

import java.net.Socket;
import java.net.ServerSocket;
import bsh.*;

/**
	BeanShell remote session server.
	Starts instances of bsh for client connections.
*/
public class Sessiond extends Thread
{
	private ServerSocket ss;
	NameSpace globalNameSpace;

	/*
	public static void main(String argv[]) throws IOException
	{
		new Sessiond( Integer.parseInt(argv[0])).start();
	}
	*/

	public Sessiond(NameSpace globalNameSpace, int port) throws IOException
	{
		ss = new ServerSocket(port);
		this.globalNameSpace = globalNameSpace;
	}

	public void run()
	{
		try
		{
			while(true)
				new SessiondConnection(globalNameSpace, ss.accept()).start();
		}
		catch(IOException e) { System.out.println(e); }
	}
}

class SessiondConnection extends Thread
{
	NameSpace globalNameSpace;
	Socket client;

	SessiondConnection(NameSpace globalNameSpace, Socket client)
	{
		this.client = client;
		this.globalNameSpace = globalNameSpace;
	}

	public void run()
	{
		try
		{
			InputStream in = client.getInputStream();
			PrintStream out = new PrintStream(client.getOutputStream());
			new Interpreter(in, out, out, true, globalNameSpace).run();
		}
		catch(IOException e) { System.out.println(e); }
	}
}

