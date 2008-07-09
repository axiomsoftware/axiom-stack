README.txt
----------------------------------------

* If the Java virtual machine is not in your path, you will need to open start.bat (Windows) or start.sh (Unix/Linux/Mac) and edit JAVA_HOME to the 
appropriate location of the java virtual machine on your system.  You will need a JVM of version 5.0 or higher.  Examples of setting JAVA_HOME:
  - JAVA_HOME = C:\Program Files\Java\jre1.6.0_03 (Windows)
  - JAVA_HOME = /path/to/java/installation
* To start Axiom, double click on start.bat (Windows) or run ./start.sh (Unix/Linux/Mac).  Execute permissions should be set on those files, but in case 
they are not, chmod a+x start.sh should take care of that.
* The distribution comes with a default server.properties file with lines commented out in the root folder.  
* The db folder contains Axiom's default embedded database storage.  Do not delete the contents in there or change them in any way!
* The lib folder contains the JAR files that Axiom requires to run.  Do not delete the contents of them!  If you want to add some Java code of your own to 
have accessible in to your application, put those JAR files in the lib directory and they will automatically be picked up by the JVM.
* The apps folder contains folders for each of the Axiom applications.  You will notice two folders in there, manage and blog.  Those are the two default 
applications distributed by Axiom.  They are available at the URLs http://host/axiom-manage and http://host/blog, respectively.  Any other applications you 
want to create will have their own folder inside the apps folder. 
* The log folder contains system and application logs to enable you to see whats going on in your applications and debug them as necessary.
* For more information, documentation, and tutorials, please visit http://axiomstack.com
