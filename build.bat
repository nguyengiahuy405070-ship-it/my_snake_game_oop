@echo off
echo ==============================================
echo  Compiling Snake Arena: Infinite Ecosystem (Java)
echo ==============================================

if not exist bin mkdir bin
if not exist bin\assets xcopy /E /I /Y src\assets bin\assets >nul

javac -encoding UTF-8 -d bin -sourcepath src src/com/snakearena/Main.java

if %ERRORLEVEL% EQU 0 (
    echo [SUCCESS] Compilation finished successfully!
) else (
    echo [ERROR] Compilation failed.
)
pause
