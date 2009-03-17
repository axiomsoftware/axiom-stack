#!/bin/bash
CLASSPATH=lib/axiom.jar:lib/axiom-lucene.jar:lib/h2.jar
java -cp $CLASSPATH axiom.db.utils.LuceneUtils db/manage

