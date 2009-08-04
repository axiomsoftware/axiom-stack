#!/bin/bash

ant jar

rm -Rf test
mkdir test
mkdir test/apps

cp -r ./lib test/
cp -r ./apps/test test/apps/
mv test/apps/test/app.properties.run test/apps/test/app.properties
rm -rf test/db/*

cd test
./start.sh
