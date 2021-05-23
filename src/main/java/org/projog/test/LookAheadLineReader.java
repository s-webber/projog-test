/*
 * Copyright 2021 S. Webber
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.projog.test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

/** Reads lines from a test script. Keeps track of the line number so that it can be included in exception messages. */
class LookAheadLineReader {
   private final File file;
   private final List<String> lines;
   private int lineNumber = -1;
   private int mark;

   LookAheadLineReader(File file) throws IOException {
      this.file = file;
      this.lines = Files.readAllLines(file.toPath());
   }

   String readLine() {
      if (lineNumber == lines.size() - 1) {
         return null;
      }

      lineNumber++;
      return lines.get(lineNumber);
   }

   void mark() {
      mark = lineNumber;
   }

   void reset() {
      lineNumber = mark;
   }

   File getFile() {
      return file;
   }

   int getLineNumber() {
      return lineNumber;
   }

   String getLine() {
      if (lineNumber < 0 || lineNumber >= lines.size()) {
         return null;
      } else {
         return lines.get(lineNumber);
      }
   }
}
