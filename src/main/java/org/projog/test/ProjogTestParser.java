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

import static org.projog.test.Markup.ERROR;
import static org.projog.test.Markup.FAIL;
import static org.projog.test.Markup.LINK;
import static org.projog.test.Markup.NO;
import static org.projog.test.Markup.OUTPUT;
import static org.projog.test.Markup.QUERY;
import static org.projog.test.Markup.QUIT;
import static org.projog.test.Markup.TRUE;
import static org.projog.test.Markup.TRUE_NO;
import static org.projog.test.Markup.YES;

import java.io.File;
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
 * <li>Test that that the query <code>?- test.</code> succeeds once and no attempt will be made to find an alternative
 * solution: <pre>
 * %TRUE test
 * </pre></li>
 * <li>Test that that the query <code>?- test.</code> succeeds once and will fail when an attempt is made to find an
 * alternative solution: <pre>
 * %TRUE_NO test
 * </pre></li>
 * <li>Test that that the query <code>?- test.</code> will fail on the first attempt to evaluate it: <pre>
 * %FAIL test
 * </pre></li>
 * <li>Test that that the query <code>?- test.</code> will succeed three times and there will be no attempt to evaluate
 * it for a fourth time: <pre>
 * %?- test
 * %YES
 * %YES
 * %YES
 * </pre></li>
 * <li>Test that that the query <code>?- test.</code> will succeed three times and will fail when an attempt is made to
 * evaluate it for a fourth time: <pre>
 * %?- test
 * %YES
 * %YES
 * %YES
 * %NO
 * </pre></li> </pre></li>
 * <li>Test that that the query <code>?- repeat.</code> will succeed three times. The {@code %QUIT} markup indicates
 * that after the third attempt then stop testing for alternative solutions, even though they may exist. <pre>
 * %?- repeat
 * %YES
 * %YES
 * %YES
 * %QUIT
 * </pre></li>
 * <li>Test that that the query <code>?- test(X).</code> will succeed three times and there will be no attempt to
 * evaluate it for a fourth time, specifying expectations about variable unification: <pre>
 * %?- test(X)
 * % X=a
 * % X=b
 * % X=c
 * </pre> The test contains the following expectations about variable unification:
 * <ul>
 * <li>After the first attempt the variable <code>X</code> will be instantiated to <code>a</code>.</li>
 * <li>After the second attempt the variable <code>X</code> will be instantiated to <code>b</code>.</li>
 * <li>After the third attempt the variable <code>X</code> will be instantiated to <code>c</code>.</li>
 * </ul>
 * </li>
 * <li>Test that that the query <code>?- test(X,Y).</code> will succeed three times and will fail when an attempt is
 * made to evaluate it for a fourth time, specifying expectations about variable unification: <pre>
 * %?- test(X,Y)
 * % X=a
 * % Y=1
 * % X=b
 * % Y=2
 * % X=c
 * % Y=3
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
 * <li>Test that that the query <code>?- repeat(3), write('hello world'), nl.</code> will succeed three times and there
 * will be no attempt to evaluate it for a fourth time, specifying expectations about what should be written to standard
 * output: <pre>
 * %?- repeat(3), write('hello world'), nl
 * %OUTPUT
 * % hello world
 * %
 * %OUTPUT
 * %YES
 * %OUTPUT
 * % hello world
 * %
 * %OUTPUT
 * %YES
 * %OUTPUT
 * % hello world
 * %
 * %OUTPUT
 * %YES
 * </pre> The test contains expectations that every evaluation will cause the text <code>hello world</code> and a
 * new-line character to be written to the standard output stream.</li>
 * <li>Test that while evaluating the query <code>?- repeat(X).</code> an exception will be thrown with a particular
 * message: <pre>
 * %?- repeat(X)
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
public final class ProjogTestParser {
   private static final String COMMENT_CHARACTER = "%";

   private final LookAheadLineReader br;

   /**
    * @throws RuntimeException if script has no tests and no links
    */
   public static List<ProjogTestQuery> getQueries(File testScript) {
      boolean hasLinks = false;
      try {
         ProjogTestParser p = new ProjogTestParser(testScript);
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
            throw new IllegalStateException("Could not find any tests or links");
         }
         return queries;
      } catch (Exception e) {
         throw new RuntimeException("Exception parsing test script: " + testScript, e);
      }
   }

   public ProjogTestParser(File testScript) throws IOException {
      br = new LookAheadLineReader(testScript);
   }

   public ProjogTestContent getNext() {
      try {
         final String line = br.readLine();
         if (line == null) {
            // end of file
            return null;
         } else if (LINK.isMatch(line)) {
            return new ProjogTestLink(LINK.parseText(line));
         } else if (TRUE_NO.isMatch(line)) {
            ProjogTestQuery query = createSingleCorrectAnswerWithNoAssignmentsQuery(line, TRUE_NO);
            query.setContinuesUntilFails();
            return query;
         } else if (TRUE.isMatch(line)) {
            return createSingleCorrectAnswerWithNoAssignmentsQuery(line, TRUE);
         } else if (FAIL.isMatch(line)) {
            String queryStr = FAIL.parseText(line);
            // no answers
            ProjogTestQuery query = new ProjogTestQuery(queryStr);
            query.setContinuesUntilFails();
            return query;
         } else if (QUERY.isMatch(line)) {
            return getQuery(line);
         } else if (isIllegalComment(line)) {
            throw new IllegalArgumentException("Unknown sys-test markup: " + line);
         } else if (isStandardComment(line)) {
            return new ProjogTestComment(getComment(line));
         } else {
            return new ProjogTestCode(line);
         }
      } catch (Exception e) {
         String message = "Line number: " + br.getLineNumber() + " line: " + br.getLine();
         throw new RuntimeException(message, e);
      }
   }

   private ProjogTestQuery createSingleCorrectAnswerWithNoAssignmentsQuery(String line, Markup markup) {
      String queryStr = markup.parseText(line);
      ProjogTestQuery queryWithSingleCorrectAnswer = new ProjogTestQuery(queryStr);
      // single correct answer with no assignments
      queryWithSingleCorrectAnswer.getAnswers().add(new ProjogTestAnswer());
      return queryWithSingleCorrectAnswer;
   }

   private ProjogTestQuery getQuery(final String line) throws IOException {
      String queryStr = QUERY.parseText(line);
      ProjogTestQuery query = new ProjogTestQuery(queryStr);
      query.getAnswers().addAll(getAnswers());
      br.mark();
      String nextLine = br.readLine();
      if (OUTPUT.isMatch(nextLine)) {
         String expectedOutput = readLinesUntilNextTag(nextLine, OUTPUT);
         query.setExpectedOutput(expectedOutput);
         query.setContinuesUntilFails();

         br.mark();
         nextLine = br.readLine();
      }

      if (QUIT.isMatch(nextLine)) {
         query.setQuitsBeforeFindingAllAnswers();
      } else if (NO.isMatch(nextLine)) {
         query.setContinuesUntilFails();
      } else if (ERROR.isMatch(nextLine)) {
         String expectedExceptionMessage = readLinesUntilNextTag(nextLine, ERROR);
         query.setExpectedExceptionMessage(expectedExceptionMessage);
      } else {
         br.reset();
      }

      return query;
   }

   private List<ProjogTestAnswer> getAnswers() throws IOException {
      List<ProjogTestAnswer> answers = new ArrayList<>();
      ProjogTestAnswer first = null;
      ProjogTestAnswer answer;
      while ((answer = getAnswer()) != null) {
         if (first == null) {
            first = answer;
         } else if (!answer.getVariableIds().equals(first.getVariableIds())) {
            throw new RuntimeException("Answers have different variable Ids: " + first.getVariableIds() + " versus: " + answer.getVariableIds());
         }
         answers.add(answer);
      }
      return answers;
   }

   private ProjogTestAnswer getAnswer() throws IOException {
      br.mark();
      String line = br.readLine();
      if (line == null) {
         // end of file
         return null;
      }
      if (line.trim().isEmpty()) {
         // blank line
         return null;
      }

      ProjogTestAnswer answer = new ProjogTestAnswer();

      if (OUTPUT.isMatch(line)) {
         String expectedOutput = readLinesUntilNextTag(line, OUTPUT);
         line = br.readLine();
         if (line == null) {
            br.reset();
            return null;
         }
         answer.setExpectedOutput(expectedOutput);
      }

      if (YES.isMatch(line)) {
         // query succeeds but no variables to check
         return answer;
      }

      if (isAssignment(line)) {
         Assignment a;
         while ((a = getAssignment(line)) != null && !answer.hasVariableId(a.getVariableId())) {
            answer.addAssignment(a.getVariableId(), a.getExpectedValue());
            br.mark();
            line = br.readLine();
         }

         br.reset();
         return answer;
      } else {
         br.reset();
         return null;
      }
   }

   private boolean isAssignment(String line) {
      return getAssignment(line) != null;
   }

   private Assignment getAssignment(String line) {
      if (line == null) {
         return null;
      }

      line = line.trim();
      if (!line.startsWith("%")) {
         return null;
      }

      if (Markup.isMarkup(line)) {
         return null;
      }

      line = line.substring(1).trim();

      int equalsPos = line.indexOf('=');
      if (equalsPos == -1) {
         return null;
      }

      return new Assignment(line.substring(0, equalsPos).trim(), line.substring(equalsPos + 1).trim());
   }

   private String readLinesUntilNextTag(String line, Markup markup) throws IOException {
      String textOnSameLineAsTag = markup.parseText(line);
      if (textOnSameLineAsTag.length() > 0) {
         return textOnSameLineAsTag;
      } else {
         StringBuilder sb = new StringBuilder();
         boolean first = true;
         while (!markup.isMatch(line = br.readLine())) {
            if (first) {
               first = false;
            } else {
               sb.append(System.lineSeparator());
            }
            sb.append(line.substring(line.indexOf('%') + 1));
         }
         return sb.toString();
      }
   }

   private String getComment(final String line) throws IOException {
      StringBuilder comment = new StringBuilder(line.substring(1).trim());
      br.mark();
      String next;
      while ((next = br.readLine()) != null && isStandardComment(next)) {
         comment.append(' ');
         comment.append(next.substring(1).trim());
         br.mark();
      }
      br.reset();
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
      if (!line.startsWith(COMMENT_CHARACTER)) {
         return false;
      } else if (line.length() == 1) {
         return false;
      } else if (!Character.isWhitespace(line.charAt(1))) {
         return false;
      } else {
         return true;
      }
   }

   /**
    * Returns {@code true} if {@code line} represents a "mark-up" comment.
    * <p>
    * In this context, a "mark-up" comment is a comment used to provide specify system tests. Mark-up comments are
    * identified by having no whitespace directly after the {@code %} comment character.
    */
   private boolean isIllegalComment(final String line) {
      String trimmed = line.trim();
      if (!trimmed.startsWith(COMMENT_CHARACTER)) {
         return false;
      }
      if (trimmed.length() == 1) {
         return false;
      }
      if (!Character.isWhitespace(trimmed.charAt(1))) {
         return true;
      }

      String sanitised = COMMENT_CHARACTER + trimmed.substring(1).trim().toUpperCase();
      if (Markup.isMarkup(sanitised)) {
         return true;
      }

      return false;
   }
}
