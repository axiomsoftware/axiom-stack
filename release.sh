#!/bin/bash

if [ "$1" ]; then
	sed 's/public static final String version.*$/public static final String version = "'$1'";/' src/java/axiom/main/Server.java > src/java/axiom/main/Server.java.new
	mv src/java/axiom/main/Server.java.new src/java/axiom/main/Server.java
	bzr commit src/java/axiom/main/Server.java -m "Release $1"
else
	echo usage: release.sh version
fi

