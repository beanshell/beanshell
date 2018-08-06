/** Copyright 2018 beanshell.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License. */
package bsh;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** A drop in replace for java.io.FileReader with implicit UTF-8 encoding.
 * Also backs up as a InyutStreamReader using a UTF-8 charset. */
@SuppressWarnings("resource") // handled by StreamDecoder
final public class FileReader extends InputStreamReader {

    /** Creates a new <tt>FileReader</tt>, given the name of the
     * file to read from.
     * @param fileName the name of the file to read from
     * @exception  FileNotFoundException  if the named file does not exist,
     *                   is a directory rather than a regular file,
     *                   or for some other reason cannot be opened for
     *                   reading. */
    public FileReader(String fileName) throws FileNotFoundException {
        this(new FileInputStream(fileName));
    }

    /** Creates a new <tt>FileReader</tt>, given the <tt>File</tt>
     * to read from.
     * @param file the <tt>File</tt> to read from
     * @exception  FileNotFoundException  if the file does not exist,
     *                   is a directory rather than a regular file,
     *                   or for some other reason cannot be opened for
     *                   reading. */
    public FileReader(File file) throws FileNotFoundException {
        this(new FileInputStream(file));
    }

    /** Creates a new <tt>FileReader</tt>, given the
     * <tt>FileDescriptor</tt> to read from.
     * @param fd the FileDescriptor to read from */
    public FileReader(FileDescriptor fd) {
        this(new FileInputStream(fd));
    }

    /** Creates a new <tt>FileReader</tt> that uses UTF-8 charset.
     * @param  in   An InputStream*/
    public FileReader(InputStream in) {
        super(in, StandardCharsets.UTF_8);
    }
}
