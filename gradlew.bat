@ECHO OFF
SETLOCAL EnableDelayedExpansion

SET DIRNAME=%~dp0
IF "%DIRNAME%"=="" SET DIRNAME=.
SET APP_HOME=%DIRNAME%
SET CLASSPATH=%APP_HOME%\gradle\wrapper\gradle-wrapper.jar

IF DEFINED JAVA_HOME (
    SET "JAVA_HOME=%JAVA_HOME:"=%"
    IF "!JAVA_HOME:~-1!"=="\" SET "JAVA_HOME=!JAVA_HOME:~0,-1!"
    IF "!JAVA_HOME:~-1!"=="/" SET "JAVA_HOME=!JAVA_HOME:~0,-1!"
    SET "JAVACMD=!JAVA_HOME!\bin\java.exe"
    IF NOT EXIST "!JAVACMD!" (
        ECHO ERROR: JAVA_HOME is set to an invalid directory: !JAVA_HOME!
        EXIT /B 1
    )
) ELSE (
    SET JAVACMD=java.exe
)

"%JAVACMD%" -Dorg.gradle.appname=%~n0 -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*
