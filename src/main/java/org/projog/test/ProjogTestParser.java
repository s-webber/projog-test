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

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses system test files to produce {@link ProjogTestContent} objects.
 * <p>
 * System test files contain both standard Prolog syntax plus extra detail contained in comments which specify queries
 * and their expected results. The system tests have two purposes:
 * <ol>
 * <li>To confirm that the Projog software works as required by comparing the expected results contained in the system
 * tests against the actual results generated when evaluating the queries.</li>
 * <li>To produce the examples contained in the web based Projog manual.</li>
 * </ol>
 * </p>
 * <p>
 * Examples of how system tests can be specified using comments (i.e. lines prefixed with a <code>%</code>) are:
 * <ol>
 * <li>Test that that the query <code>?- test().</code> succeeds once and no attempt will be made to find an alternative
 * solution: <pre>
 * %TRUE test1()
 * </pre></li>
 * <li>Test that that the query <code>?- test().</code> succeeds once and will fail when an attempt is made to find an
 * alternative solution: <pre>
 * %TRUE_NO test1()
 * </pre></li>
 * <li>Test that that the query <code>?- test().</code> will fail on the first attempt to evaluate it: <pre>
 * %FALSE test1()
 * </pre></li>
 * <li>Test that that the query <code>?- test().</code> will succeed three times and there will be no attempt to
 * evaluate it for a fourth time: <pre>
 * %QUERY test()
 * %ANSWER/
 * %ANSWER/
 * %ANSWER/
 * </pre></li>
 * <li>Test that that the query <code>?- test().</code> will succeed three times and will fail when an attempt is made
 * to evaluate it for a fourth time: <pre>
 * %QUERY test()
 * %ANSWER/
 * %ANSWER/
 * %ANSWER/
 * %NO
 * </pre></li>
 * <li>Test that that the query <code>?- test(X).</code> will succeed three times and there will be no attempt to
 * evaluate it for a fourth time, specifying expectations about variable unification: <pre>
 * %QUERY test(X)
 * %ANSWER X=a
 * %ANSWER X=b
 * %ANSWER X=c
 * </pre> The test contains the following expectations about variable unification:
 * <ul>
 * <li>After the first attempt the variable <code>X</code> will be instantiated to <code>a</code>.</li>
 * <li>After the second attempt the variable <code>X</code> will be instantiated to <code>b</code>.</li>
 * <li>After the third attempt the variable <code>X</code> will be instantiated to <code>c</code>.</li>
 * </ul>
 * </li>
 * <li>Test that that the query <code>?- test(X,Y).</code> will succeed three times and will fail when an attempt is
 * made to evaluate it for a fourth time, specifying expectations about variable unification: <pre>
 * %QUERY test(X,Y)
 * %ANSWER
 * X=a
 * Y=1
 * %ANSWER
 * %ANSWER
 * X=b
 * Y=2
 * %ANSWER
 * %ANSWER
 * X=c
 * Y=3
 * %ANSWER
 * %NO
 * </pre> The test contains the following expectations about variable unification:
 * <ul>
 * <li>After the first attempt the variable <code>X</code> will be instantiated to <code>a</code> and the variable
 * <code>Y</code> will be instantiated to <code>1</code>.</li>
 * <li>After the second attempt the variable <code>X</code> will be instantiated to <code>b</code> and the variable
 * <code>Y</code> will be instantiated to <code>2</code>.</li>
 * <li>After the third attempt the variable <code>X</code> will be instantiated to <code>c</code> and the variable
 * <code>Y</code> will be instantiated to <code>3</code>.</li>
 * </ul>
 * </li>
 * <li>Test that that the query <code>?- test().</code> will succeed three times and there will be no attempt to
 * evaluate it for a fourth time, specifying expectations about what should be written to standard output: <pre>
 * %QUERY repeat(3), write('hello world'), nl
 * %OUTPUT
 * % hello world
 * %
 * %OUTPUT
 * %ANSWER/
 * %OUTPUT
 * % hello world
 * %
 * %OUTPUT
 * %ANSWER/
 * %OUTPUT
 * % hello world
 * %
 * %OUTPUT
 * %ANSWER/
 * </pre> The test contains expectations that every evaluation will cause the text <code>hello world</code> and a
 * new-line character to be written to the standard output stream.</li>
 * <li>Test that while evaluating the query <code>?- repeat(X).</code> an exception will be thrown with a particular
 * message: <pre>
 * %QUERY repeat(X)
 * %ERROR Expected Numeric but got: NAMED_VARIABLE with value: X
 * </pre></li>
 * <li>The following would be ignored when running the system tests but would be used when constructing the web based
 * documentation to include a link to <code>test.html</code>: <pre>
 * %LINK test
 * </pre></li>
 * </ol>
 * </p>
 * <img src="doc-files/ProjogTestParser.png">
 */
public final class ProjogTestParser implements Closeable {
   private static final String COMMENT_CHARACTER = "%";
   private static final String TRUE_TAG = "%TRUE";
   private static final String TRUE_NO_TAG = "%TRUE_NO";
   private static final String NO_TAG = "%NO";
   private static final String QUIT_TAG = "%QUIT";
   private static final String FALSE_TAG = "%FALSE";
   private static final String QUERY_TAG = "%QUERY";
   private static final String ANSWER_TAG = "%ANSWER";
   private static final String ANSWER_NO_VARIABLES_TAG = "%ANSWER/";
   private static final String OUTPUT_TAG = "%OUTPUT";
   private static final String EXCEPTION_TAG = "%ERROR";
   private static final String LINK_TAG = "%LINK";

   private final BufferedReader br;

   /**
    * @throws RuntimeException if script has no tests and no links
    */
   static List<ProjogTestQuery> getQueries(File testScript) {
      boolean hasLinks = false;
      try (ProjogTestParser p = new ProjogTestParser(testScript)) {
         List<ProjogTestQuery> queries = new ArrayList<>();
         ProjogTestContent c;
         while ((c = p.getNext()) != null) {
            if (c instanceof ProjogTestQuery) {
               queries.add((ProjogTestQuery) c);
            } else if (c instanceof ProjogTestLink) {
               hasLinks = true;
            }
         }
         if (queries.isEmpty() && !hasLinks) {
            throw new RuntimeException("could not find any tests or links in: " + testScript);
         }
         return queries;
      } catch (IOException e) {
         throw new RuntimeException("Exception parsing test script: " + testScript, e);
      }
   }

   public ProjogTestParser(File testScript) throws FileNotFoundException {
      FileReader fr = new FileReader(testScript);
      br = new BufferedReader(fr);
   }

   public ProjogTestContent getNext() throws IOException {
      final String line = br.readLine();
      if (line == null) {
         // end of file
         return null;
      } else if (line.startsWith(LINK_TAG)) {
         return new ProjogTestLink(getText(line).trim());
      } else if (line.startsWith(TRUE_NO_TAG)) {
         ProjogTestQuery query = createSingleCorrectAnswerWithNoAssignmentsQuery(line);
         query.setContinuesUntilFails();
         return query;
      } else if (line.startsWith(TRUE_TAG)) {
         return createSingleCorrectAnswerWithNoAssignmentsQuery(line);
      } else if (line.startsWith(FALSE_TAG)) {
         String queryStr = getText(line);
         // no answers
         ProjogTestQuery query = new ProjogTestQuery(queryStr);
         query.setContinuesUntilFails();
         return query;
      } else if (line.startsWith(QUERY_TAG)) {
         return getQuery(line);
      } else if (isStandardComment(line)) {
         return new ProjogTestComment(getComment(line));
      } else if (isMarkupComment(line)) {
         throw new IllegalArgumentException("Unknown sys-test markup: " + line);
      } else {
         return new ProjogTestCode(line);
      }
   }

   private ProjogTestQuery createSingleCorrectAnswerWithNoAssignmentsQuery(String line) {
      String queryStr = getText(line);
      ProjogTestQuery queryWithSingleCorrectAnswer = new ProjogTestQuery(queryStr);
      // single correct answer with no assignments
      queryWithSingleCorrectAnswer.getAnswers().add(new ProjogTestAnswer());
      return queryWithSingleCorrectAnswer;
   }

   private ProjogTestQuery getQuery(final String line) throws IOException {
      String queryStr = getText(line);
      ProjogTestQuery query = new ProjogTestQuery(queryStr);
      query.getAnswers().addAll(getAnswers());
      mark();
      String nextLine = br.readLine();
      if (nextLine != null && nextLine.startsWith(OUTPUT_TAG)) {
         String expectedOutput = readLinesUntilNextTag(nextLine, OUTPUT_TAG);
         query.setExpectedOutput(expectedOutput);
         query.setContinuesUntilFails();

         mark();
         nextLine = br.readLine();
      }
      if (nextLine != null && equalsIgnoringLeadingAndTrailingWhitespace(nextLine, QUIT_TAG)) {
         query.setQuitsBeforeFindingAllAnswers();
      } else if (nextLine != null && equalsIgnoringLeadingAndTrailingWhitespace(nextLine, NO_TAG)) {
         query.setContinuesUntilFails();
      } else if (nextLine != null && nextLine.startsWith(EXCEPTION_TAG)) {
         String expectedExceptionMessage = readLinesUntilNextTag(nextLine, EXCEPTION_TAG);
         query.setExpectedExceptionMessage(expectedExceptionMessage);
      } else {
         reset();
      }
      return query;
   }

   private List<ProjogTestAnswer> getAnswers() throws IOException {
      List<ProjogTestAnswer> answers = new ArrayList<>();
      ProjogTestAnswer answer;
      while ((answer = getAnswer()) != null) {
         answers.add(answer);
      }
      return answers;
   }

   private ProjogTestAnswer getAnswer() throws IOException {
      mark();
      String line = br.readLine();
      if (line == null) {
         // end of file
         return null;
      }
      ProjogTestAnswer answer = new ProjogTestAnswer();

      if (line.startsWith(OUTPUT_TAG)) {
         String expectedOutput = readLinesUntilNextTag(line, OUTPUT_TAG);
         line = br.readLine();
         if (line == null) {
            reset();
            return null;
         }
         answer.setExpectedOutput(expectedOutput);
      }
      if (line.startsWith(ANSWER_NO_VARIABLES_TAG)) {
         // query succeeds but no variables to check
         return answer;
      } else if (equalsIgnoringLeadingAndTrailingWhitespace(line, ANSWER_TAG)) {
         // query succeeds with variables to check
         addAssignments(answer);
         return answer;
      } else if (line.startsWith(ANSWER_TAG)) {
         // query succeeds with single variable to check
         addAssignment(answer, line);
         return answer;
      } else {
         reset();
         return null;
      }
   }

   private void addAssignments(ProjogTestAnswer answer) throws IOException {
      String next;
      while (!(next = br.readLine()).startsWith(ANSWER_TAG)) {
         addAssignment(answer, next);
      }
   }

   private void addAssignment(ProjogTestAnswer answer, String line) {
      String assignmentStatement = getText(line);
      int equalsPos = assignmentStatement.indexOf('=');
      String variableId = assignmentStatement.substring(0, equalsPos).trim();
      String expectedValue = assignmentStatement.substring(equalsPos + 1).trim();
      answer.addAssignment(variableId, expectedValue);
   }

   private String readLinesUntilNextTag(String line, String tagName) throws IOException {
      String expectedOutput = getText(line);
      if (expectedOutput.length() == 0) {
         boolean first = true;
         StringBuilder sb = new StringBuilder();
         while (!(line = br.readLine()).startsWith(tagName)) {
            line = line.substring(line.indexOf('%') + 1);
            boolean addNewLine = (first && line.length() == 0) || !first;
            if (addNewLine) {
               sb.append(System.lineSeparator());
            }
            sb.append(line);
            first = false;
         }
         expectedOutput = sb.toString();
      }
      return expectedOutput;
   }

   private String getComment(final String line) throws IOException {
      StringBuilder comment = new StringBuilder(line.substring(1).trim());
      mark();
      String next;
      while ((next = br.readLine()) != null && isStandardComment(next)) {
         comment.append(' ');
         comment.append(next.substring(1).trim());
         mark();
      }
      reset();
      return comment.toString().trim();
   }

   /**
    * Returns {@code true} if {@code line} represents a "standard" comment.
    * <p>
    * In this context, a "standard" comment is a comment used to provide descriptive human readable messages - rather
    * than "mark-up" comments used to specify system tests. Standard comments are identified by having whitespace
    * directly after the {@code %} comment character.
    */
   private boolean isStandardComment(final String line) {
      return line.startsWith(COMMENT_CHARACTER) && line.length() > 1 && Character.isWhitespace(line.charAt(1));
   }

   /**
    * Returns {@code true} if {@code line} represents a "mark-up" comment.
    * <p>
    * In this context, a "mark-up" comment is a comment used to provide specify system tests. Mark-up comments are
    * identified by having no whitespace directly after the {@code %} comment character.
    */
   private boolean isMarkupComment(final String line) {
      return line.startsWith(COMMENT_CHARACTER) && !isStandardComment(line) && !line.trim().equals(COMMENT_CHARACTER);
   }

   /**
    * Get text minus any sys-test markup.
    *
    * @param line e.g.: {@code %QUERY X is 1}
    * @return e.g.: {@code X is 1}
    */
   private static String getText(String line) {
      line = line.trim();
      int spacePos = line.indexOf(' ');
      if (spacePos == -1) {
         return "";
      } else {
         return line.substring(spacePos + 1).trim();
      }
   }

   private static boolean equalsIgnoringLeadingAndTrailingWhitespace(String a, String b) {
      return a.trim().equals(b.trim());
   }

   private void mark() throws IOException {
      br.mark(1024);
   }

   private void reset() throws IOException {
      br.reset();
      br.mark(0);
   }

   @Override
   public void close() {
      try {
         if (br != null) {
            br.close();
         }
      } catch (Exception e) {
         e.printStackTrace();
      }
   }
}
