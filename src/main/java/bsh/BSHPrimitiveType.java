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



package bsh;

class BSHPrimitiveType extends SimpleNode
{
    public Class<?> type;

    BSHPrimitiveType(int id) { super(id); }

    BSHPrimitiveType(bsh.congo.tree.PrimitiveType pt) {
        super(ParserTreeConstants.JJTPRIMITIVETYPE, pt);
        switch(pt.firstChildOfType(Token.class).getType()) {
            case BOOLEAN : type = Boolean.TYPE; break;
            case BYTE : type = Byte.TYPE; break;
            case CHAR : type = Character.TYPE; break;
            case INT : type = Integer.TYPE; break;
            case LONG : type = Long.TYPE; break;
            case FLOAT : type = Float.TYPE; break;
            case DOUBLE : type = Double.TYPE; break;
            case SHORT : type = Short.TYPE; break;
            default : break;
        }        
    }

    public Class<?> getType() { return type; }


    @Override
    public String toString() {
        return super.toString() + ": " + type;
    }
}

