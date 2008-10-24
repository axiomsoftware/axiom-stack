ant jar

mkdir test
mkdir test/apps

cp -r lib/ test/
cp -r apps/test test/apps/
mv test/apps/test/app.properties.run test/apps/test/app.properties
cp apps/test/server.properties test/
cp start.bat test/
cp launcher.jar test/
rm -rf test/db/*

cd test
./start.bat
