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
package bsh.gui;

import bsh.Interpreter;
import bsh.Capabilities;
import bsh.EvalError;

/**
    Console startup class.
*/
public class Console
{
    public static void main( String args[] ) {

        if ( !Capabilities.classExists( "bsh.gui.Util" ) )
            System.out.println("Can't find the BeanShell utilities...");

        if ( bsh.gui.GuiCapabilities.haveSwing() )
        {
            bsh.gui.Util.startSplashScreen();
            try {
                Interpreter interpreter = new Interpreter();
                interpreter.eval("desktop()");
            } catch ( EvalError e ) {
                System.err.println("Couldn't start desktop: "+e);
            }
        } else {
            System.err.println(
                "Can't find javax.swing package: "
            +" An AWT based Console is available but not built by default.");
            //AWTConsole.main( args );
        }
    }
}
