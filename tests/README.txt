Running the Unit Tests
----------------------

Use the tool of your choice to run the unit tests located unter

	tests/junitTests/src/bsh

You could also use the provided ant target:

	ant junit-tests

The test class "bsh.OldScriptsTest" runs the 'legacy' test srcipts,
see description below.


===================
Legacy test scripts
===================

Running the Test Scripts
------------------------

Before running the test suite be sure to compile the java classes in the 
src directory and any subdirs and add this to your classpath.

Then run the RunAllTests.bsh script:

	java bsh.Interpreter RunAllTests.bsh

RunAllTests.bsh will run every file in test with a file extension of ".bsh"
except itself and TestHarness.bsh.  At the end the test suite will report a 
summary of any failures or warnings.

There is also an ant target for this:

	ant test


Writing Test Scripts
--------------------

Scripts here should source testharness.bsh 

Use assert() to verify what's working.
Use fail() (or assert(false)) to indicate failure.

You can use the flag() counter to note that something happened:

	// Verify that 'if' works
	if ( true )
		flag();

	assert( flag() == 1 );  // Note: flag() is now 2

Call complete() as the last thing your script does.  If you do not call
complete it will be assume that the script failed.


Files in test
-------------

Data/
	Misc data files and auxilliary scripts used by 
	the test scripts.

Interactive/
	Ad-hoc scripts / apps which currently must be run interactively to test.
	The goal will be to move all of this into automated tests.

src/
	Java classes used by the scripts. e.g. to test calling into particular
	Java structures and packaging.


