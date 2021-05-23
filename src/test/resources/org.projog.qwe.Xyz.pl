% Tests use of %?- and expected variable assignments.

%?- X is 1+1
% X=2

%?- X is 1+1
% X=3

%?- X is 4-1
% X=3

%?- X is 1+1, Y=3
% X=2

%?- X is 1+1, Y=3, Z=4
% W=2
% X=2
% Y=3
% Z=4

%?- X=3, repeat(X)
% X=3
% X=3

%?- Y=3, repeat(Y)
% Y=3
% Y=3
% Y=3

%?- Z=3, repeat(Z)
% Z=3
% Z=3
% Z=3
% Z=3
