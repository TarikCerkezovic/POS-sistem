@echo off
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
if not exist out\META-INF mkdir out\META-INF
copy /Y resources\META-INF\persistence.xml out\META-INF\persistence.xml >nul
echo Kompajliranje zavrseno.
pause
