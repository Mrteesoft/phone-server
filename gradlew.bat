@ECHO OFF
SETLOCAL

SET DIRNAME=%~dp0
IF "%DIRNAME%"=="" SET DIRNAME=.
SET APP_HOME=%DIRNAME%
SET CLASSPATH=%APP_HOME%\gradle\wrapper\gradle-wrapper.jar

IF DEFINED JAVA_HOME (
    SET JAVACMD=%JAVA_HOME%\bin\java.exe
    IF NOT EXIST "%JAVACMD%" (
        ECHO ERROR: JAVA_HOME is set to an invalid directory: %JAVA_HOME%
        EXIT /B 1
    )
) ELSE (
    SET JAVACMD=java.exe
)

"%JAVACMD%" -Dorg.gradle.appname=%~n0 -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*
