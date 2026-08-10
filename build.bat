@echo off
rem Kompajliranje POS sistema (Windows)
if not exist out mkdir out
dir /s /b src\*.java > sources.txt
javac -encoding UTF-8 -cp "lib/*" -d out @sources.txt
if errorlevel 1 (
    del sources.txt
    echo Kompajliranje NEUSPJESNO.
    pause
    exit /b 1
)
del sources.txt
echo Kompajliranje zavrseno.
pause
