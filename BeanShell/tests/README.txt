
Running the Test Scripts
------------------------

Before running the test suite be sure to compile the java clases in the 
classes directory and any subdirs and add this to your classpath.

Then run the RunAllTests.bsh script:

	java bsh.Interpreter RunAllTests.bsh

RunAllTests.bsh will run every file in test with a file extension of ".bsh"
except itself and TestHarness.bsh.  At the end the test suite will report a 
summary of any failures or warnings.


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
	Scripts which must be run interactively to test.

classes/
	Java classes used by the scripts. e.g. to test calling into particular
	Java structures and packaging.


