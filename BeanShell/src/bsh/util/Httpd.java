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

import java.io.DataInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.util.StringTokenizer;
import java.net.Socket;
import java.net.ServerSocket;

/**
	A very simple httpd that supports the remote server mode.
	Files are loaded relative to the classpath (as resources).

	Note: at some point this should be recast as a beanshell script.
*/
public class Httpd extends Thread
{
	ServerSocket ss;

	public static void main(String argv[]) throws IOException
	{
		new Httpd(Integer.parseInt(argv[0])).start();
	}

	public Httpd(int port) throws IOException
	{
		ss = new ServerSocket(port);
	}

	public void run()
	{
//		System.out.println("starting httpd...");
		try
		{
			while(true)
				new HttpdConnection(ss.accept()).start();
		}
		catch(IOException e)
		{
			System.out.println(e);
		}
	}
}

class HttpdConnection extends Thread
{
	Socket client;
	DataInputStream din;
	OutputStream out;
	PrintStream pout;
	boolean isHttp1;

	HttpdConnection(Socket client)
	{
		this.client = client;
		setPriority(NORM_PRIORITY - 1);
	}

	public void run()
	{
//		System.out.println("new http connection");
		try
		{
			din = new DataInputStream(client.getInputStream());
			out = client.getOutputStream();
			pout = new PrintStream(out);

			String request = din.readLine();
//			System.out.println( "Request: " + request );

			if(request.toLowerCase().indexOf("http/1.") != -1)
			{
				String s;
				while((!(s = din.readLine()).equals("")) && (s != null))
				{ ; }

				isHttp1 = true;
			}

			StringTokenizer st = new StringTokenizer(request);
			if(st.countTokens() < 2) 
				error(400, "Bad Request");
			else
			{
				String command = st.nextToken();
				if(command.equals("GET"))
					serveFile(st.nextToken());
				else
					error(400, "Bad Request");
			}

			client.close();
		}
		catch(IOException e)
		{
			System.out.println("I/O error " + e); 
			try
			{
				client.close();
			}
			catch(Exception e2) { }
		}
	}

	private void serveFile(String file) throws FileNotFoundException, IOException
	{
		if(file.equals("/"))
			file = "/bsh/util/lib/remote.html";
/*
		if(file.startsWith("/"))
			file = file.substring(1);
		if(file.endsWith("/") || file.equals(""))
			file = file + "index.html";

		if(!fileAccessOK(file))
		{
			error(403, "Forbidden");
			return;
		}
*/

		try
		{
			sendFileData(file);
		}
		catch(FileNotFoundException e)
		{
			error(404, "Object Not Found");
		}
	}

	private void sendFileData(String file) throws IOException, FileNotFoundException
	{
		/*
			Why aren't resources being found when this runs on Win95?
		*/
		InputStream fis = getClass().getResourceAsStream(file);
		if(fis == null)
			throw new FileNotFoundException(file);

		byte[] data = new byte[fis.available()];
		fis.read(data);

		if(isHttp1)
		{
			pout.println("HTTP/1.0 200 Document follows");
			if(file.endsWith(".gif"))
				pout.println("Content-type: image/gif");
			else 
				pout.println("Content-type: text/html");
/*
			else if(file.endsWith(".html") || file.endsWith(".htm"))
				pout.println("Content-Type: text/html");
			else
				pout.println("Content-Type: application/octet-stream");
*/
			pout.println("Content-length: " + data.length + "\n");
		}
		out.write(data);
		out.flush();
	}

	private void error(int num, String s)
	{
		s = "<html><h1>" + s + "</h1></html>";
		if(isHttp1)
		{
			pout.println("HTTP/1.0 " + num + " " + s);
			pout.println("Content-type: text/html");
			pout.println("Content-length: " + s.length() + "\n");
		}
		
		pout.println(s);
	}
}

