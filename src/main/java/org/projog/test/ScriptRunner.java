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

import static org.projog.test.ProjogTestUtils.toUnixLineEndings;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.projog.api.Projog;
import org.projog.api.QueryResult;
import org.projog.api.QueryStatement;
import org.projog.core.ProjogException;
import org.projog.core.event.ProjogListener;
import org.projog.core.event.SpyPoints.SpyPointEvent;
import org.projog.core.event.SpyPoints.SpyPointExitEvent;
import org.projog.core.term.Atom;
import org.projog.core.term.Term;

/** Runs the tests contained in an individual test script. */
final class ScriptRunner implements ProjogListener {
   private static final boolean DEBUG = false;

   private final File redirectedOutputFile = new File(getClass().getName() + "_" + hashCode() + ".tmp");
   private final Map<Object, Integer> spypointSourceIds = new HashMap<>();
   private final File f;
   private final ProjogTestRunnerConfig config;
   private final Projog projog;
   private final ScriptResults testResults;

   ScriptRunner(ProjogTestRunnerConfig config, File f) {
      this.config = config;
      this.projog = config.createProjog();
      projog.addListener(this);
      this.testResults = new ScriptResults(f);
      this.f = f;
   }

   ScriptResults checkScript() {
      long start = System.currentTimeMillis();
      try {
         consultFile(f);
         List<ProjogTestQuery> queries = ProjogTestParser.getQueries(f);
         checkQueries(queries);
      } catch (Exception e) {
         debug(e);
         StringBuilder msg = new StringBuilder(e.getMessage());
         Throwable cause = e;
         while ((cause = cause.getCause()) != null) {
            if (cause.getMessage() != null) {
               msg.append(' ').append(cause.getMessage());
            }
         }
         testResults.addError("Error checking script: " + msg);
      }
      testResults.duration = System.currentTimeMillis() - start;
      return testResults;
   }

   private void consultFile(File script) {
      println("Checking script: " + script.getPath());
      projog.consultFile(script);
   }

   private void checkQueries(List<ProjogTestQuery> queries) {
      for (ProjogTestQuery query : queries) {
         try {
            checkQuery(query);
         } catch (Exception e) {
            debug(e);
            testResults.addError("Query: " + query.getPrologQuery() + " Error: " + e.getMessage());
         }
      }
   }

   private void checkQuery(ProjogTestQuery query) {
      debug("QUERY: " + query.getPrologQuery());
      testResults.queryCount++;

      Iterator<ProjogTestAnswer> expectedAnswers = null;
      Term redirectedOutputFileHandle = null;
      try {
         redirectedOutputFileHandle = redirectOutput();

         QueryResult result = config.executeQuery(projog, query.getPrologQuery() + ".");
         expectedAnswers = query.getAnswers().iterator();

         boolean isExhausted = result.isExhausted();
         spypointSourceIds.clear();
         while (result.next()) {
            if (isExhausted) {
               throw new RuntimeException("isExhausted() was true when there were still more answers available");
            }
            debug("ANSWERS:");
            if (!expectedAnswers.hasNext()) {
               if (query.doesQuitBeforeFindingAllAnswers()) {
                  return;
               } else {
                  throw new RuntimeException("More answers than expected");
               }
            }
            ProjogTestAnswer correctAnswer = expectedAnswers.next();
            checkOutput(correctAnswer);
            checkAnswer(result, correctAnswer);

            isExhausted = result.isExhausted();

            closeOutput(redirectedOutputFileHandle);
            redirectedOutputFileHandle = redirectOutput();
            spypointSourceIds.clear();
         }
         if (expectedAnswers.hasNext()) {
            throw new RuntimeException("Less answers than expected");
         }
         if (query.doesQuitBeforeFindingAllAnswers()) {
            throw new RuntimeException("Found all answers before quit");
         }
         if (isExhausted && query.isContinuesUntilFails()) {
            throw new RuntimeException("Did not have to fail before determining there were no more answers");
         }
         if (!isExhausted && query.doesNotContinueUntilFails() && !config.doIgnoreFailedRetries()) {
            throw new RuntimeException("Had to fail to determine there were no more answers");
         }
         if (query.isContinuesUntilFails()) {
            checkOutput(query.getExpectedOutput());
         }
         if (query.getExpectedExceptionMessage() != null) {
            throw new RuntimeException("Query did not produce the expected error: " + query.getExpectedExceptionMessage());
         }
      } catch (ProjogException pe) {
         debug(pe);
         String actual = pe.getMessage();
         String expected = query.getExpectedExceptionMessage();
         if (actual.equals(expected) == false) {
            throw new RuntimeException("Expected: >" + expected + "< but got: >" + actual + "<", pe);
         }
         if (expectedAnswers != null && expectedAnswers.hasNext()) {
            throw new RuntimeException("Less answers than expected", pe);
         }
      } finally {
         closeOutput(redirectedOutputFileHandle);
      }
   }

   /**
    * Redirect output to a file, rather than <code>System.out</code>, so it can be checked against the expectations.
    *
    * @return a reference to the newly opened file so it can be closed and read from after the tests have run
    * @see #closeOutput(Term)
    */
   private Term redirectOutput() {
      redirectedOutputFile.delete();
      QueryStatement openStmt = projog.createStatement("open('" + redirectedOutputFile.getName() + "',write,Z).");
      QueryResult openResult = openStmt.executeQuery();
      if (!openResult.next()) {
         throw new IllegalStateException();
      }
      Term redirectedOutputFileHandle = openResult.getTerm("Z");
      QueryStatement setOutputStmt = projog.createStatement("set_output(Z).");
      setOutputStmt.setTerm("Z", redirectedOutputFileHandle);
      QueryResult setOutputResult = setOutputStmt.executeQuery();
      if (!setOutputResult.next()) {
         throw new IllegalStateException();
      }
      return redirectedOutputFileHandle;
   }

   private void checkOutput(ProjogTestAnswer answer) {
      checkOutput(answer.getExpectedOutput());
   }

   private void checkOutput(String expected) {
      byte[] redirectedOutputFileContents = readAllBytes(redirectedOutputFile);
      String actual = new String(redirectedOutputFileContents);
      if (!equalExcludingLineTerminators(expected, actual)) {
         throw new RuntimeException("Expected: >\n" + expected + "\n< but got: >\n" + actual + "\n<");
      }
   }

   public static byte[] readAllBytes(File f) {
      try {
         return Files.readAllBytes(f.toPath());
      } catch (Exception e) {
         throw new RuntimeException("could not read file: " + f, e);
      }
   }

   private static boolean equalExcludingLineTerminators(String expected, String actual) {
      return toUnixLineEndings(expected).equals(toUnixLineEndings(actual));
   }

   /**
    * Close the file that was used to redirect output from the tests.
    *
    * @param redirectedOutputFileHandle reference to the file to close
    * @see #redirectOutput()
    */
   private void closeOutput(Term redirectedOutputFileHandle) {
      QueryStatement closeStmt = projog.createStatement("close(Z).");
      closeStmt.setTerm("Z", redirectedOutputFileHandle);
      QueryResult closeResult = closeStmt.executeQuery();
      if (!closeResult.next()) {
         throw new IllegalStateException();
      }
      redirectedOutputFile.delete();
   }

   private void checkAnswer(QueryResult result, ProjogTestAnswer correctAnswer) {
      Set<String> variableIds = result.getVariableIds();
      if (variableIds.size() != correctAnswer.getAssignmentsCount()) {
         throw new RuntimeException("Different number of variables than expected. Actual: " + variableIds + " Expected: " + correctAnswer.getAssignments());
      }

      for (String variableId : variableIds) {
         Term variable = result.getTerm(variableId);
         String actualTerm = projog.formatTerm(variable);

         String expectedTerm = correctAnswer.getAssignedValue(variableId);
         if (expectedTerm == null) {
            throw new RuntimeException(variableId
                                       + " ("
                                       + variable
                                       + ") was not expected to be assigned to anything but was to: "
                                       + actualTerm
                                       + " "
                                       + correctAnswer.getAssignments());
         }

         if ("UNINSTANTIATED VARIABLE".equals(expectedTerm)) {
            if (!variable.getType().isVariable()) {
               throw new RuntimeException(variableId
                                          + " ("
                                          + variable
                                          + ") assigned to: "
                                          + actualTerm
                                          + " of type: "
                                          + variable.getType()
                                          + " but expected: "
                                          + expectedTerm
                                          + " "
                                          + correctAnswer.getAssignments());
            }
         } else if (!actualTerm.equals(expectedTerm)) {
            throw new RuntimeException(variableId + " (" + variable + ") assigned to: " + actualTerm + " not: " + expectedTerm + " " + correctAnswer.getAssignments());
         }
      }
   }

   @Override
   public void onCall(SpyPointEvent event) {
      update("CALL", event);
   }

   @Override
   public void onRedo(SpyPointEvent event) {
      update("REDO", event);
   }

   @Override
   public void onExit(SpyPointExitEvent event) {
      update("EXIT", event);
   }

   @Override
   public void onFail(SpyPointEvent event) {
      update("FAIL", event);
   }

   @Override
   public void onWarn(String message) {
      // system tests do not include expectations about WARN or INFO events - so don't check them
      // TODO start including WARN and INFO events in test expectations
      println(message);
   }

   @Override
   public void onInfo(String message) {
      // system tests do not include expectations about WARN or INFO events - so don't check them
      // TODO start including WARN and INFO events in test expectations
      println(message);
   }

   /**
    * Writes to the output stream {@code ProjogEvent}s generated by running the tests.
    * <p>
    * Notified of events as is registered as an observer of {@link #projog}
    */
   private void update(String level, SpyPointEvent event) {
      String message = generateLogMessageForEvent(level, event);
      writeLogMessage(message);
   }

   /**
    * @return e.g. <code>[2] CALL testCalculatables( X, 3, 7 )</code>
    */
   private String generateLogMessageForEvent(String level, SpyPointEvent event) {
      String actualSourceId = Integer.toString(event.getSourceId());
      Integer i = spypointSourceIds.get(actualSourceId);
      if (i == null) {
         i = spypointSourceIds.size() + 1;
         spypointSourceIds.put(actualSourceId, i);
      }
      return "[" + i + "] " + level + " " + event.toString();
   }

   private void writeLogMessage(String message) {
      QueryStatement openStmt = projog.createStatement("write(Message), nl.");
      openStmt.setTerm("Message", new Atom(message));
      QueryResult openResult = openStmt.executeQuery();
      if (!openResult.next()) {
         throw new IllegalStateException();
      }
   }

   private static void debug(String s) {
      if (DEBUG) {
         println(s);
      }
   }

   private void debug(Exception e) {
      if (DEBUG) {
         e.printStackTrace(System.out);
      }
   }

   private static void println(String s) {
      System.out.println(s);
   }

   /**
    * Represents the results of running the Prolog tests.
    */
   public static class ScriptResults implements Comparable<ScriptResults> {
      private final File f;
      private final List<String> errorMessages = new ArrayList<>();
      private int queryCount;
      private int errorCount;
      private long duration;

      private ScriptResults(File f) {
         this.f = f;
      }

      File getFile() {
         return f;
      }

      private void addError(String errorDescription) {
         errorMessages.add(errorDescription);
      }

      int getQueryCount() {
         return queryCount;
      }

      int getErrorCount() {
         return errorMessages.size();
      }

      boolean hasFailures() {
         return errorCount != 0;
      }

      List<String> getErrorMessages() {
         return Collections.unmodifiableList(errorMessages);
      }

      long getDuration() {
         return duration;
      }

      @Override
      public int compareTo(ScriptResults o) {
         return f.compareTo(o.f);
      }
   }
}
