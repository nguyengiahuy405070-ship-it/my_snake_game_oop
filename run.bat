@echo off
echo ==============================================
echo  Launching Snake Arena: Infinite Ecosystem (Java)
echo ==============================================

if not exist bin\assets (
    if not exist bin mkdir bin
    xcopy /E /I /Y src\assets bin\assets >nul
)

if not exist bin\com\snakearena\Main.class (
    echo [INFO] Binaries not found, compiling first...
    javac -encoding UTF-8 -d bin -sourcepath src src/com/snakearena/Main.java
)

java -cp bin com.snakearena.Main
