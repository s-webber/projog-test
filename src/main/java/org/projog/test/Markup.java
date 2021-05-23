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

/**
 * Represents the different types of markup that can be included as comments in test scripts.
 * <p>
 * Markup is included in test scripts to define the queries to be tested and their expected results.
 */
enum Markup {
   /**
    * Used to specify a query that evaluates once and no attempt will be made to re-evaluate.
    */
   TRUE,
   /**
    * Used to specify a query that evaluates once and then fails when re-evaluated.
    */
   TRUE_NO,
   /**
    * Used to specify that a query without variables has been successfully evaluated.
    */
   YES(true),
   /**
    * Used to specify that an attempt to re-evaluate a query did not find any further solutions.
    */
   NO(true),
   /**
    * Used to simulate a user choosing to no longer evaluate a query even though further solutions may exist.
    */
   QUIT(true),
   /**
    * Used to specify a query that does not produce any solutions.
    */
   FAIL,
   /**
    * Used to specify query. Subsequent lines specify the result of executing the query.
    */
   QUERY("?-"),
   /**
    * Used to specify a output that was produced while evaluating a query.
    */
   OUTPUT,
   /**
    * Used to specify the error message that was produced while evaluating a query.
    */
   ERROR,
   /**
    * Used to specify a link to another part of the documentation.
    */
   LINK;

   private final String prefix;
   private final boolean neverHasText;

   Markup() {
      this(false);
   }

   Markup(boolean neverHasText) {
      this.prefix = "%" + toString();
      this.neverHasText = neverHasText;
   }

   Markup(String name) {
      this.prefix = "%" + name;
      this.neverHasText = false;
   }

   boolean isMatch(String line) {
      if (line == null) {
         return false;
      }

      String trimmed = line.trim();

      if (trimmed.equals(prefix)) {
         return true;
      } else if (trimmed.startsWith(prefix + " ")) {
         if (neverHasText) {
            throw new RuntimeException("Did not expect text after : " + prefix + " but got: " + line);
         } else {
            return true;
         }
      } else {
         return false;
      }
   }

   /**
    * Get text minus the leading tag name.
    *
    * @param line e.g.: {@code %?- X is 1}
    * @return e.g.: {@code X is 1}
    */
   String parseText(String line) {
      line = line.trim();

      if (line.equals(prefix)) {
         return "";
      }

      if (!line.startsWith(prefix)) {
         throw new RuntimeException("Expected line to start with \"" + prefix + "\"");
      }

      if (!line.startsWith(prefix + " ")) {
         throw new RuntimeException("Expected line to have space after \"" + prefix + "\"");
      }

      int spacePos = line.indexOf(' ');
      if (spacePos == -1) {
         // should never get here as have checked above that line contains a space
         throw new IllegalStateException();
      }
      return line.substring(spacePos + 1).trim();
   }

   static boolean isMarkup(String line) {
      for (Markup m : values()) {
         if (m.isMatch(line)) {
            return true;
         }
      }

      return false;
   }
}
