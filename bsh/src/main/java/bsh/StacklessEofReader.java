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

import java.io.FilterReader;
import java.io.IOException;
import java.io.Reader;

/** Parser input that signals end of input with a reused exception carrying no stack trace. */
final class StacklessEofReader extends FilterReader {
    private IOException endOfInput;

    private StacklessEofReader(Reader in) {
        super(in);
    }

    static Reader wrap(Reader in) {
        return null == in || in instanceof StacklessEofReader ? in : new StacklessEofReader(in);
    }

    // JavaCharStream turns -1 into a new IOException, whose stack trace costs time proportional to stack depth.
    @Override
    public int read(char[] buf, int off, int len) throws IOException {
        if (null != endOfInput) {
            // The caller may have reset or replaced the underlying reader, so try a
            // real read before rethrowing the cached EOF exception. If the reader
            // errors instead (e.g. it was already closed), rethrow the cached
            // exception rather than propagate a fresh, real stack trace.
            int count;
            try {
                count = in.read(buf, off, len);
            } catch (IOException e) {
                throw endOfInput;
            }
            if (-1 == count)
                throw endOfInput;
            endOfInput = null;
            return count;
        }
        int count = in.read(buf, off, len);
        if (-1 == count) {
            // Do not close the underlying reader: it is owned by the caller and
            // may need to be reused or reset.
            endOfInput = new EndOfInput();
            throw endOfInput;
        }
        return count;
    }

    private static final class EndOfInput extends IOException {
        private static final long serialVersionUID = 1L;

        EndOfInput() {
            super("end of input");
        }

        @Override
        public synchronized Throwable fillInStackTrace() {
            return this;
        }
    }
}
