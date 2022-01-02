/*
 * Copyright 2013 S. Webber
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

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Represents an answer to a query contained in a system test file.
 *
 * @see ProjogTestParser
 */
public final class ProjogTestAnswer {
   /**
    * Text expected to be written to the standard output stream as part of determining this answer.
    */
   private String expectedOuput = "";

   /**
    * Values expected to be assigned to query variables as part of determining this answer.
    * <p>
    * Key = variable id. Value = String representation of assigned term.
    */
   private final Map<String, String> assignments = new TreeMap<>();

   void setExpectedOutput(String expectedOuput) {
      this.expectedOuput = expectedOuput;
   }

   public String getExpectedOutput() {
      return expectedOuput;
   }

   void addAssignment(String variableId, String expectedValue) {
      if (hasVariableId(variableId)) {
         throw new IllegalStateException("Duplicate values provided for variable: " + variableId);
      }
      assignments.put(variableId, expectedValue);
   }

   public boolean hasVariableId(String key) {
      return assignments.containsKey(key);
   }

   public String getAssignedValue(String variableId) {
      return assignments.get(variableId);
   }

   public Set<String> getVariableIds() {
      return new TreeSet<>(assignments.keySet());
   }

   public Set<Map.Entry<String, String>> getAssignments() {
      return new TreeMap<>(assignments).entrySet();
   }

   public int getAssignmentsCount() {
      return assignments.size();
   }

   @Override
   public String toString() {
      return "[" + super.toString() + " " + assignments.toString() + "]";
   }
}
