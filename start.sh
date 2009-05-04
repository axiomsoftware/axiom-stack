#!/usr/bin/env bash
# Shell script for starting Axiom with a JDK-like virtual machine.

# To add JAR files to the classpath, simply place them into the
# lib/ext directory.

# Guess which jre to use.
JAVA_HOME="`which java`"
JAVA_HOME=${JAVA_HOME/%bin\/java/}

if [ "$JAVA_HOME" == "" ]; then
    case "`uname`" in
	Darwin*)
	    JAVA_HOME=/usr/
	    ;;
	*)
	    #Only uncomment the line below if your instance of Java does not reside in /bin.
	    #JAVA_HOME=/usr/jdk/jdk1.6.0
	    ;;
    esac
fi
echo "JAVA_HOME=$JAVA_HOME"

# uncomment to set AXIOM_HOME, otherwise we get it from the script path
# AXIOM_HOME=/usr/local/axiom

# options to pass to the Java virtual machine
# JAVA_OPTIONS="-server -Xmx128m"

# Set TCP ports for Axiom servers
# (comment/uncomment to de/activate)
HTTP_PORT=8080
# XMLRPC_PORT=8081
# AJP13_PORT=8009
# RMI_PORT=5050

# Set file encoding
ENCODING=UTF-8
# Set FSDirectory implementation for Lucene
FSDIRECTORY=org.apache.lucene.store.TransFSDirectory

###########################################################
###### No user configuration needed below this line #######
###########################################################

# if JAVA_HOME variable is set, use it. Otherwise, Java executable
# must be contained in PATH variable.
if [ "$JAVA_HOME" ]; then
   JAVACMD="$JAVA_HOME/bin/java"
   # Check if java command is executable
   if [ ! -x $JAVACMD ]; then
      echo "Warning: JAVA_HOME variable may be set incorrectly:"
      echo "         No executable found at $JAVACMD"
   fi
else
   JAVACMD=java
fi

# Get the Axiom installation directory
INSTALL_DIR="${0%/*}"
cd $INSTALL_DIR
INSTALL_DIR=$PWD

# get AXIOM_HOME variable if it isn't set
if [ -z "$AXIOM_HOME" ]; then
  # try to get AXIOM_HOME from script file and pwd
  # strip everyting behind last slash
  AXIOM_HOME="${0%/*}"
  cd $AXIOM_HOME
  AXIOM_HOME=$PWD
else
  cd $AXIOM_HOME
fi
echo "Starting Axiom in directory $AXIOM_HOME"

if [ "$HTTP_PORT" ]; then
   SWITCHES="$SWITCHES -w $HTTP_PORT"
   echo Starting HTTP server on port $HTTP_PORT
fi
if [ "$XMLRPC_PORT" ]; then
   SWITCHES="$SWITCHES -x $XMLRPC_PORT"
   echo Starting XML-RPC server on port $XMLRPC_PORT
fi
if [ "$AJP13_PORT" ]; then
   SWITCHES="$SWITCHES -jk $AJP13_PORT"
   echo Starting AJP13 listener on port $AJP13_PORT
fi
if [ "$RMI_PORT" ]; then
   SWITCHES="$SWITCHES -p $RMI_PORT"
   echo Starting RMI server on port $RMI_PORT
fi
if [ "$AXIOM_HOME" ]; then
   SWITCHES="$SWITCHES -h $AXIOM_HOME"
fi

# Invoke the Java VM
$JAVACMD -Dfile.encoding=$ENCODING -Dorg.apache.lucene.FSDirectory.class=$FSDIRECTORY $JAVA_OPTIONS -jar "$INSTALL_DIR/launcher.jar" $SWITCHES
