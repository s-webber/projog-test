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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;

import com.tngtech.java.junit.dataprovider.DataProvider;
import com.tngtech.java.junit.dataprovider.DataProviderRunner;

@RunWith(DataProviderRunner.class)
public class ProjogTestParserTest {
   @Test
   public void testTrue() throws IOException {
      ProjogTestQuery query = parseSingleItem("%TRUE X = 9");

      assertEquals("X = 9", query.getPrologQuery());
      assertNull(query.getExpectedExceptionMessage());
      assertEquals("", query.getExpectedOutput());
      assertFalse(query.isContinuesUntilFails());
      assertTrue(query.doesNotContinueUntilFails());
      assertFalse(query.doesQuitBeforeFindingAllAnswers());
      assertSingleEmptyAnswer(query);
   }

   @Test
   public void testTrueNo() throws IOException {
      ProjogTestQuery query = parseSingleItem("%TRUE_NO X = 9");

      assertEquals("X = 9", query.getPrologQuery());
      assertNull(query.getExpectedExceptionMessage());
      assertEquals("", query.getExpectedOutput());
      assertTrue(query.isContinuesUntilFails());
      assertFalse(query.doesNotContinueUntilFails());
      assertFalse(query.doesQuitBeforeFindingAllAnswers());
      assertSingleEmptyAnswer(query);
   }

   @Test
   public void testFalse() throws IOException {
      ProjogTestQuery query = parseSingleItem("%FAIL p(x,y,z) >= [X|Y]");

      assertEquals("p(x,y,z) >= [X|Y]", query.getPrologQuery());
      assertNull(query.getExpectedExceptionMessage());
      assertEquals("", query.getExpectedOutput());
      assertTrue(query.isContinuesUntilFails());
      assertFalse(query.doesNotContinueUntilFails());
      assertFalse(query.doesQuitBeforeFindingAllAnswers());
      assertTrue(query.getAnswers().isEmpty());
   }

   @Test
   public void testAnswersNoVariables() throws IOException {
      ProjogTestQuery query = parseSingleItem("%?- X = 9", "%YES", "%YES", "%YES", "%YES", "%YES");

      assertEquals("X = 9", query.getPrologQuery());
      assertNull(query.getExpectedExceptionMessage());
      assertEquals("", query.getExpectedOutput());
      assertFalse(query.isContinuesUntilFails());
      assertTrue(query.doesNotContinueUntilFails());
      assertFalse(query.doesQuitBeforeFindingAllAnswers());
      assertEmptyAnswers(query, 5);
   }

   @Test
   public void testAnswersNoVariablesContinueUntilFails() throws IOException {
      ProjogTestQuery query = parseSingleItem("%?- X = 9", "%YES", "%YES", "%YES", "%NO");

      assertEquals("X = 9", query.getPrologQuery());
      assertNull(query.getExpectedExceptionMessage());
      assertEquals("", query.getExpectedOutput());
      assertTrue(query.isContinuesUntilFails());
      assertFalse(query.doesNotContinueUntilFails());
      assertFalse(query.doesQuitBeforeFindingAllAnswers());
      assertEmptyAnswers(query, 3);
   }

   @Test
   public void testAnswersNoVariablesQuit() throws IOException {
      ProjogTestQuery query = parseSingleItem("%?- X = 9", "%YES", "%YES", "%YES", "%QUIT");

      assertEquals("X = 9", query.getPrologQuery());
      assertNull(query.getExpectedExceptionMessage());
      assertEquals("", query.getExpectedOutput());
      assertFalse(query.isContinuesUntilFails());
      assertTrue(query.doesNotContinueUntilFails());
      assertTrue(query.doesQuitBeforeFindingAllAnswers());
      assertEmptyAnswers(query, 3);
   }

   @Test
   @DataProvider({"%YES", "%NO", "%QUIT"})
   public void testUnexpectedText(String markup) throws IOException {
      try {
         parseSingleItem("%?- X = 9", "%YES", "%YES", "%YES", markup + " x");
         fail();
      } catch (RuntimeException e) {
         assertEquals("Line number: 4 line: " + markup + " x", e.getMessage());
         assertEquals("Did not expect text after : " + markup + " but got: " + markup + " x", e.getCause().getMessage());
      }
   }

   @Test
   public void testLink() throws IOException {
      ProjogTestLink link = parseSingleItem("%LINK prolog-test.pl");

      assertLink("prolog-test.pl", link);
   }

   @Test
   public void testException() throws IOException {
      ProjogTestQuery query = parseSingleItem("%?- X = 9", "%ERROR error text");

      assertEquals("X = 9", query.getPrologQuery());
      assertEquals("error text", query.getExpectedExceptionMessage());
      assertEquals("", query.getExpectedOutput());
      assertFalse(query.isContinuesUntilFails());
      assertTrue(query.doesNotContinueUntilFails());
      assertTrue(query.getAnswers().isEmpty());
   }

   @Test
   public void testErrorOnRetry() throws IOException {
      ProjogTestQuery query = parseSingleItem("%?- X = 9", "%YES", "%ERROR exception on retry");

      assertEquals("X = 9", query.getPrologQuery());
      assertEquals("exception on retry", query.getExpectedExceptionMessage());
      assertEquals("", query.getExpectedOutput());
      assertFalse(query.isContinuesUntilFails());
      assertTrue(query.doesNotContinueUntilFails());
      assertFalse(query.doesQuitBeforeFindingAllAnswers());
      assertSingleEmptyAnswer(query);
   }

   @Test
   public void testMultipleVariableAssignmentSingleSolution() throws IOException {
      ProjogTestQuery query = parseSingleItem("%?- X = 9", "% X=4", "% Y=[X|Y]");

      List<ProjogTestAnswer> answers = query.getAnswers();
      assertEquals(1, answers.size());
      ProjogTestAnswer answer = answers.get(0);
      assertEquals("", answer.getExpectedOutput());
      assertEquals(2, answer.getAssignments().size());
      assertAssignment(answer, "X", "4");
      assertAssignment(answer, "Y", "[X|Y]");
   }

   @Test
   public void testMultipleVariableAssignmentMultipleSolutions() throws IOException {
      ProjogTestQuery query = parseSingleItem("%?- X = 9", "%X=a", "% Y = b ", "% X =c", "%Y= d");

      List<ProjogTestAnswer> answers = query.getAnswers();
      assertEquals(2, answers.size());

      ProjogTestAnswer answer = answers.get(0);
      assertEquals("", answer.getExpectedOutput());
      assertEquals(2, answer.getAssignments().size());
      assertAssignment(answer, "X", "a");
      assertAssignment(answer, "Y", "b");

      answer = answers.get(1);
      assertEquals("", answer.getExpectedOutput());
      assertEquals(2, answer.getAssignments().size());
      assertAssignment(answer, "X", "c");
      assertAssignment(answer, "Y", "d");
   }

   @Test
   public void testInconsistentVariableIds() throws IOException {
      try {
         parseSingleItem("%?- test(X, Y)", "%X=a", "% Y=b", "%X=a", "% Y=b", "% X=c", "% Z=d");
         fail();
      } catch (RuntimeException e) {
         assertEquals("Line number: 6 line: % Z=d", e.getMessage());
         assertEquals("Answers have different variable Ids: [X, Y] versus: [X, Z]", e.getCause().getMessage());
      }
   }

   @Test
   public void testSingleVariableAssignment() throws IOException {
      ProjogTestQuery query = parseSingleItem("%?- X = 9", "% X=453", "% X=[X|Y]");

      List<ProjogTestAnswer> answers = query.getAnswers();
      assertEquals(2, answers.size());
      assertSingleAssignment(answers.get(0), "X", "453");
      assertSingleAssignment(answers.get(1), "X", "[X|Y]");
   }

   @Test
   public void testSingleLineOutput() throws IOException {
      ProjogTestQuery query = parseSingleItem("%?- X = 9", "%OUTPUT example of single line output", "%YES");

      List<ProjogTestAnswer> answers = query.getAnswers();
      assertEquals(1, answers.size());
      assertEquals("example of single line output", answers.get(0).getExpectedOutput());
   }

   @Test
   public void testMultiLineOutput() throws IOException {
      ProjogTestQuery query = parseSingleItem("%?- X = 9", "%OUTPUT", "%example of   ", "% multi line output", "%OUTPUT", "%YES");

      List<ProjogTestAnswer> answers = query.getAnswers();
      assertEquals(1, answers.size());
      assertEquals("example of   " + System.lineSeparator() + " multi line output", answers.get(0).getExpectedOutput());
   }

   @Test
   public void testOutputAndError() throws IOException {
      ProjogTestQuery query = parseSingleItem("%?- X = 9", "%OUTPUT output from first attempt  ", "%YES", "%OUTPUT output from retry", "%ERROR an error message");

      List<ProjogTestAnswer> answers = query.getAnswers();
      assertEquals(1, answers.size());
      assertEquals("output from first attempt", answers.get(0).getExpectedOutput());
      assertEquals("output from retry", query.getExpectedOutput());
      assertEquals("an error message", query.getExpectedExceptionMessage());
   }

   @Test
   public void testCombinations() throws IOException {
      ProjogTestParser parser = parse("% hello", "% world", "%TRUE true", "a line of code", "another line of code", "%LINK xyz", "final line of code", "% end");

      assertComment("hello world", parser);
      assertQueryString("true", parser);
      assertCode("a line of code", parser);
      assertCode("another line of code", parser);
      assertLink("xyz", parser);
      assertCode("final line of code", parser);
      assertComment("end", parser);
      assertNull(parser.getNext());
   }

   @Test
   public void testComment() throws IOException {
      ProjogTestParser p = parse("% Note: this is a comment.");
      ProjogTestComment comment = (ProjogTestComment) p.getNext();
      assertEquals("Note: this is a comment.", comment.getComment());
   }

   @Test
   @DataProvider({"%QWERTY", "%FALSE", "% ?- true", "%true xyz", "%YES", "%NO", "%ERROR xyz", "%OUTPUT xyz"})
   public void testUnknownMarkup(String command) throws IOException {
      ProjogTestParser p = parse(command);

      try {
         p.getNext();
         fail();
      } catch (RuntimeException e) {
         assertEquals("Line number: 0 line: " + command, e.getMessage());
         assertEquals("Unknown sys-test markup: " + command, e.getCause().getMessage());
      }
   }

   @Test
   @DataProvider({"%?-X hello", "%TRUEX hello", "%TRUE_NOX hello", "%FAILX hello"})
   public void testNoSpaceAfterMarkup(String command) throws IOException {
      ProjogTestParser p = parse(command);

      try {
         p.getNext();
         fail();
      } catch (RuntimeException e) {
         assertEquals("Line number: 0 line: " + command, e.getMessage());
         assertEquals("Unknown sys-test markup: " + command, e.getCause().getMessage());
      }
   }

   @Test
   public void testGetQueries() throws IOException {
      File scriptFile = createScriptFile("% a comment", //
                  "some code.", //
                  "%TRUE query 1", //
                  "%FAIL query 2", //
                  "% another comment", //
                  "%TRUE_NO query 3", //
                  "%?- query 4", //
                  "%YES", //
                  "%LINK xyz.pl");

      List<ProjogTestQuery> contents = ProjogTestParser.getQueries(scriptFile);
      assertEquals(4, contents.size());
      assertEquals("query 1", contents.get(0).getPrologQuery());
      assertEquals("query 2", contents.get(1).getPrologQuery());
      assertEquals("query 3", contents.get(2).getPrologQuery());
      assertEquals("query 4", contents.get(3).getPrologQuery());
   }

   @Test
   public void testNoQueries() throws IOException {
      File scriptFile = createScriptFile("% a comment", "some code.");

      try {
         ProjogTestParser.getQueries(scriptFile);
         fail();
      } catch (RuntimeException e) {
         assertEquals("Exception parsing test script: " + scriptFile, e.getMessage());
         assertEquals("Could not find any tests or links", e.getCause().getMessage());
      }
   }

   @SuppressWarnings("unchecked")
   private <T extends ProjogTestContent> T parseSingleItem(String... lines) throws IOException {
      ProjogTestParser parser = parse(lines);
      ProjogTestContent content = parser.getNext();
      assertNull(parser.getNext());
      return (T) content;
   }

   private ProjogTestParser parse(String... lines) throws IOException {
      File testScript = createScriptFile(lines);
      return new ProjogTestParser(testScript);
   }

   private File createScriptFile(String... lines) throws IOException {
      File f = File.createTempFile("ProjogTestParserTest", ".tmp", new File("target"));
      try (PrintWriter pw = new PrintWriter(f)) {
         for (String line : lines) {
            pw.println(line);
         }
      }
      f.deleteOnExit();
      return f;
   }

   private void assertComment(String expected, ProjogTestParser parser) throws IOException {
      ProjogTestComment comment = (ProjogTestComment) parser.getNext();
      assertEquals(expected, comment.getComment());
   }

   private void assertLink(String expected, ProjogTestParser parser) throws IOException {
      ProjogTestLink link = (ProjogTestLink) parser.getNext();
      assertLink(expected, link);
   }

   private void assertLink(String expected, ProjogTestLink link) {
      assertEquals(expected, link.getTarget());
   }

   private void assertSingleAssignment(ProjogTestAnswer answer, String id, String value) {
      assertEquals(1, answer.getAssignments().size());
      assertAssignment(answer, id, value);
   }

   private void assertAssignment(ProjogTestAnswer answer, String id, String value) {
      assertEquals(answer.toString(), value, answer.getAssignedValue(id));
   }

   private void assertCode(String expected, ProjogTestParser parser) throws IOException {
      ProjogTestCode code = (ProjogTestCode) parser.getNext();
      assertEquals(code.getPrologCode(), expected);
   }

   private void assertQueryString(String expectedQueryString, ProjogTestParser parser) throws IOException {
      ProjogTestQuery query = (ProjogTestQuery) parser.getNext();
      assertEquals(expectedQueryString, query.getPrologQuery());
   }

   private void assertSingleEmptyAnswer(ProjogTestQuery query) {
      assertEmptyAnswers(query, 1);
   }

   private void assertEmptyAnswers(ProjogTestQuery query, int numAnswers) {
      List<ProjogTestAnswer> answers = query.getAnswers();
      assertEquals(numAnswers, answers.size());
      for (ProjogTestAnswer answer : answers) {
         assertEmptyAnswer(answer);
      }
   }

   private void assertEmptyAnswer(ProjogTestAnswer answer) {
      assertEquals("", answer.getExpectedOutput());
      assertEquals(0, answer.getAssignmentsCount());
      assertTrue(answer.getAssignments().isEmpty());
   }
}
