% Tests use of %OUTPUT.

%?- write(hello)
%OUTPUT hello
%YES

%?- write(hello)
%OUTPUT hi
%YES

%?- write(hello), nl, write(world), nl
%OUTPUT
%hello
%world
%
%OUTPUT
%YES

%?- write(hello), nl, write(world), nl
%OUTPUT
%hello
%earth
%
%OUTPUT
%YES

z(1).
z(2).
z(3).
z(q).

%?- z(Z), write(here), write(Z), Y is Z*2
%OUTPUT here1
% Z=1
% Y=2
%OUTPUT here2
% Z=2
% Y=4
%OUTPUT here3
% Z=3
% Y=6
%OUTPUT hereq
%ERROR Cannot find arithmetic operator: q

%?- z(Z), write(here), write(Z), Y is Z*2
%OUTPUT here1
% Z=1
% Y=2
%OUTPUT here2
% Z=2
% Y=4
%OUTPUT here3
% Z=3
% Y=6
%ERROR Cannot find arithmetic operator: q

%?- z(Z), write(here), write(Z), Y is Z*2
%OUTPUT here1
% Z=1
% Y=2
%OUTPUT here2
% Z=2
% Y=4
%OUTPUT here3
% Z=3
% Y=6
%OUTPUT hereq
