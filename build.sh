#!/bin/sh
mkdir -p out
javac -encoding UTF-8 -cp "lib/*" -d out $(find src -name "*.java") || exit 1
echo "Kompajliranje uspjesno."
