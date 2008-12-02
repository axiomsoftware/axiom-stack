title Axiom Stack

@echo off
rem Batch file for Starting Axiom with a JDK-like virtual machine.

rem To add jar files to the classpath, simply place them into the 
rem lib/ext directory of this Axiom installation.

:: Initialize variables
:: (don't touch this section)
set JAVA_HOME=
set AXIOM_HOME=
set HTTP_PORT=
set XMLRPC_PORT=
set AJP13_PORT=
set RMI_PORT=
set OPTIONS=

:: Set TCP ports for Axiom servers
:: (comment/uncomment to de/activate)
set HTTP_PORT=80
rem set XMLRPC_PORT=8081
rem set AJP13_PORT=8009
rem set RMI_PORT=5050

:: Uncomment to set AXIOM_HOME
rem set AXIOM_HOME=c:\axiom

:: Uncomment to set JAVA_HOME variable
rem set JAVA_HOME=c:\program files\java

:: Uncomment to pass options to the Java virtual machine
rem set JAVA_OPTIONS=-Xms756m -Xmx1024m

:: Set file encoding
set ENCODING=UTF-8
:: Set FSDirectory implementation for Lucene
set FSDIRECTORY=org.apache.lucene.store.TransFSDirectory

:::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
:::::: No user configuration needed below this line :::::::
:::::::::::::::::::::::::::::::::::::::::::::::::::::::::::

:: Setting the script path
set INSTALL_DIR=%~d0%~p0

:: Using JAVA_HOME variable if defined. Otherwise,
:: Java executable must be contained in PATH variable
if "%JAVA_HOME%"=="" goto default
   set JAVACMD=%JAVA_HOME%\bin\java
   goto end
:default
   set JAVACMD=java
:en

:: Setting AXIOM_HOME to script path if undefined
if "%AXIOM_HOME%"=="" (
   set AXIOM_HOME=%INSTALL_DIR%
)
cd %AXIOM_HOME%


:: Setting Axiom server options
if not "%HTTP_PORT%"=="" (
   echo Starting HTTP server on port %HTTP_PORT%
   set OPTIONS=%OPTIONS% -w %HTTP_PORT%
)
if not "%XMLRPC_PORT%"=="" (
   echo Starting XML-RPC server on port %XMLRPC_PORT%
   set OPTIONS=%OPTIONS% -x %XMLRPC_PORT%
)
if not "%AJP13_PORT%"=="" (
   echo Starting AJP13 listener on port %AJP13_PORT%
   set OPTIONS=%OPTIONS% -jk %AJP13_PORT%
)
if not "%RMI_PORT%"=="" (
   echo Starting RMI server on port %RMI_PORT%
   set OPTIONS=%OPTIONS% -p %RMI_PORT%
)
if not "%AXIOM_HOME%"=="" (
   echo Serving applications from %AXIOM_HOME%
   set OPTIONS=%OPTIONS% -h "%AXIOM_HOME%
)

:: Invoking the Java virtual machine
::echo %OPTIONS%
 %JAVACMD% -javaagent:c:\dev\JavaConTest\Lib\ConTest.jar -Dfile.encoding=%ENCODING% -Dorg.apache.lucene.FSDirectory.class=%FSDIRECTORY% %JAVA_OPTIONS% -jar "%INSTALL_DIR%\launcher.jar" %OPTIONS%
