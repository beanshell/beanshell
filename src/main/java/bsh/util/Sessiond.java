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

import java.io.*;

import java.net.Socket;
import java.net.ServerSocket;
import bsh.*;

/**
	BeanShell remote session server.
	Starts instances of bsh for client connections.
	Note: the sessiond effectively maps all connections to the same interpreter
	(shared namespace).
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
			Interpreter i = new Interpreter(
				new InputStreamReader(in), out, out, true, globalNameSpace);
			i.setExitOnEOF( false ); // don't exit interp
			i.run();
		}
		catch(IOException e) { System.out.println(e); }
	}
}

