#!/bin/bash
JAVA_OPTIONS=-Dorg.apache.lucene.FSDirectory.class=org.apache.lucene.store.TransFSDirectory
CLASSPATH=lib/axiom.jar:lib/axiom-lucene.jar:lib/h2.jar
java $JAVA_OPTIONS -cp $CLASSPATH axiom.db.utils.LuceneUtils db/manage

