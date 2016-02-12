#!/usr/bin/perl -pi
#multi-line in place substitute - subs.pl
BEGIN {undef $/;}

s/^\/\*{77}.* \*{77,77}\///smg;

