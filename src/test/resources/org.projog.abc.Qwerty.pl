% Contains mixture of successful and unsuccessful tests to confirm that they are processed as expected.

% Check %TRUE with a query that suceeds and another that fails.
%TRUE 1=1
%TRUE 1=2

% Check %TRUE with a query that contains a variable.
%TRUE X=1

% Check %TRUE with a query that evaluates more than once.
%TRUE repeat(2)

% Check %FAIL with a query that suceeds and two others that fails.
%FAIL 4>8
%FAIL 4>2
%FAIL repeat

% Check %TRUE_NO with a query that fails.
%TRUE_NO 1=2

% Check %TRUE_NO with a query that suceeds and another that succeeds.
x :- true.
x :- fail.
%TRUE_NO x

%TRUE x

% More verbose way of expressing the above "%TRUE x" query
%?- x
%YES

%?- x
%YES
%NO

p(1).
p(2).
p(3).

% Example of when %NO not required
%?- p(S)
% S=1
% S=2
% S=3
%NO

% Example of when %NO is specified when there are actually more answers
%?- p(Q)
% Q=1
% Q=2
%NO

% Example of when the number of expected answers is less than actually generated.
%?- p(X)
% X=1
% X=2

% Example of when the number of expected answers is more than actually generated.
%?- p(Y)
% Y=1
% Y=2
% Y=3
% Y=4

% Example of when the test expects an error that is not actually thrown.
%?- p(Z)
% Z=1
% Z=2
%ERROR dummy error message

% Example of when the test expects an error that is not actually thrown.
%?- p(W)
% W=1
% W=2
% W=3
%ERROR dummy error message

% Correct use of %QUIT
%?- repeat
%YES
%YES
%YES
%QUIT

% Correct use of %QUIT
%?- repeat(3)
%YES
%QUIT

% Correct use of %QUIT
%?- repeat(3)
%YES
%YES
%QUIT

% Incorrect use of %QUIT
%?- repeat(3)
%YES
%YES
%YES
%QUIT

% Incorrect use of %QUIT
%?- true
%YES
%QUIT

