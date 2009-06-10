About Axiom Stack:
-----------------------------------------

Axiom is an object-oriented web application framework, like Ruby on Rails, Struts, et al, built by Axiom 
Software, Inc. It has a built-in web server, fully searchable object database, and dead-simple zen-like 
application development. Rather than a compiled language like Java, Axiom applications are written purely 
in ECMAScript (Javascript).

The Axiom Stack website is at: http://www.axiomstack.com

You will find plenty of documentation there to get started and learn how to develop web applications with
the Axiom Stack.

Our Google group is at: http://groups.google.com/group/axiom-stack

Enjoy.


How to build the source:
----------------------------------------

To build the source code into the Axiom jars, execute the following command (requires ANT to be installed):

ant -buildfile /path/to/build.xml jar

Successful completion of the jar ANT task will result in the Axiom Stack being built to lib/axiom.jar


Using Eclipse:
----------------------------------------

To use Eclipse (http://www.eclipse.org/) for working with the source please follow these steps. This assumes you have the source on your system. (below $WORKSPACE should be replaced with the actual path to your Eclipse workspace)
 - Get Eclipse (http://www.eclipse.org/downloads/)
 - Open Eclipse
   - Specify a workspace
 - Move the source into your workspace (mv stack $WORKSPACE/stack)
 - Create a new Java project
   - Project Name: stack
   - Existing Source: $WORKSPACE/stack
 - Finish

This should make it easy for you to import the source. We include our build file so you can use that to compile/jar Axiom Stack.


Dependencies:
----------------------------------------

The Axiom Stack source depends on a customized Lucene implementation.  This customized implementation comes
with the Axiom Stack and is located at lib/axiom-lucene.jar; therefore, you don't need to do anything extra
to get this customized version with the source you just downloaded.  However, if you would like the source
code for the customized Lucene version, please go to http://github.com/axiomsoftware/axiom-lucene

