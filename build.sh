#!/bin/sh
mkdir -p out
javac -encoding UTF-8 -cp "lib/*" -d out $(find src -name "*.java") || exit 1
mkdir -p out/META-INF
cp resources/META-INF/persistence.xml out/META-INF/persistence.xml
echo "Kompajliranje uspjesno."
