#!/usr/bin/env bash
# Shell script for starting Axiom Stack

# uncomment to set AXIOM_HOME, otherwise we get it from the script path
# AXIOM_HOME=/usr/local/axiom

# options to pass to the Java virtual machine
# JAVA_OPTIONS="-server -Xmx128m"

# Set TCP ports for Axiom servers
# (comment/uncomment to de/activate)
if [ -z "$HTTP_PORT" ]; then
	HTTP_PORT=8080
fi
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

# OS Support
cygwin=false;
darwin=false;
case "`uname`" in
  CYGWIN*) cygwin=true ;;
  Darwin*) darwin=true
           if [ -z "$JAVA_HOME" ] ; then
             JAVA_HOME=/System/Library/Frameworks/JavaVM.framework/Home
           fi
           ;;
esac

if $cygwin; then
    [ -n "$JAVA_HOME" ] &&
        JAVA_HOME=`cygpath --unix "$JAVA_HOME"`
fi

if [ -z "$JAVACMD" ] ; then
  if [ -n "$JAVA_HOME"  ] ; then
    if [ -x "$JAVA_HOME/jre/sh/java" ] ; then
      # IBM's JDK on AIX uses strange locations for the executables
      JAVACMD="$JAVA_HOME/jre/sh/java"
    else
      JAVACMD="$JAVA_HOME/bin/java"
    fi
  else
    JAVACMD=`which java 2> /dev/null `
    if [ -z "$JAVACMD" ] ; then
        JAVACMD=java
    fi
  fi
fi

if [ ! -x "$JAVACMD" ] ; then
  echo "Error: JAVA_HOME is not defined correctly."
  echo "  We cannot execute $JAVACMD"
  exit 1
fi

echo "JAVA=$JAVACMD"

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
