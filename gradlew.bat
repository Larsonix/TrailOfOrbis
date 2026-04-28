@rem
@rem Copyright 2015 the original author or authors.
@rem
@rem Licensed under the Apache License, Version 2.0 (the "License");
@rem you may not use this file except in compliance with the License.
@rem You may obtain a copy of the License at
@rem
@rem      https://www.apache.org/licenses/LICENSE-2.0
@rem
@rem Unless required by applicable law or agreed to in writing, software
@rem distributed under the License is distributed on an "AS IS" BASIS,
@rem WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
@rem See the License for the specific language governing permissions and
@rem limitations under the License.
@rem

@if "%DEBUG%"=="" @echo off
@rem ##########################################################################
@rem
@rem  Gradle startup script for Windows
@rem
@rem ##########################################################################

@rem Set local scope for the variables with windows NT shell
if "%OS%"=="Windows_NT" setlocal

set DIRNAME=%~dp0
if "%DIRNAME%"=="" set DIRNAME=.
@rem This is normally unused
set APP_BASE_NAME=%~n0
set APP_HOME=%DIRNAME%

@rem Resolve any "." and ".." in APP_HOME to make it shorter.
for %%i in ("%APP_HOME%") do set APP_HOME=%%~fi

@rem Add default JVM options here. You can also use JAVA_OPTS and GRADLE_OPTS to pass JVM options to this script.
set DEFAULT_JVM_OPTS="-Xmx64m" "-Xms64m"

@rem Prefer Java 21 for the Gradle launcher in this repo.
@rem Set HYTALE_GRADLE_ENFORCE_JAVA21=false to disable.
if /I not "%HYTALE_GRADLE_ENFORCE_JAVA21%"=="false" (
    set "HYTALE_JAVA21_CANDIDATE="

    if defined HYTALE_GRADLE_JAVA21_HOME if exist "%HYTALE_GRADLE_JAVA21_HOME%\bin\java.exe" set "HYTALE_JAVA21_CANDIDATE=%HYTALE_GRADLE_JAVA21_HOME%"
    if not defined HYTALE_JAVA21_CANDIDATE if defined JAVA_HOME_21_X64 if exist "%JAVA_HOME_21_X64%\bin\java.exe" set "HYTALE_JAVA21_CANDIDATE=%JAVA_HOME_21_X64%"
    if not defined HYTALE_JAVA21_CANDIDATE if exist "%USERPROFILE%\.jdks\jdk-21\bin\java.exe" set "HYTALE_JAVA21_CANDIDATE=%USERPROFILE%\.jdks\jdk-21"

    if not defined HYTALE_JAVA21_CANDIDATE for /f "delims=" %%d in ('dir /b /ad "C:\Program Files\Eclipse Adoptium\jdk-21*" 2^>nul') do (
        if not defined HYTALE_JAVA21_CANDIDATE if exist "C:\Program Files\Eclipse Adoptium\%%d\bin\java.exe" set "HYTALE_JAVA21_CANDIDATE=C:\Program Files\Eclipse Adoptium\%%d"
    )
    if not defined HYTALE_JAVA21_CANDIDATE for /f "delims=" %%d in ('dir /b /ad "C:\Program Files\Java\jdk-21*" 2^>nul') do (
        if not defined HYTALE_JAVA21_CANDIDATE if exist "C:\Program Files\Java\%%d\bin\java.exe" set "HYTALE_JAVA21_CANDIDATE=C:\Program Files\Java\%%d"
    )

    if defined HYTALE_JAVA21_CANDIDATE set "JAVA_HOME=%HYTALE_JAVA21_CANDIDATE%"
)

@rem Find java.exe
if defined JAVA_HOME goto findJavaFromJavaHome

set JAVA_EXE=java.exe
%JAVA_EXE% -version >NUL 2>&1
if %ERRORLEVEL% equ 0 goto execute

echo. 1>&2
echo ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH. 1>&2
echo. 1>&2
echo Please set the JAVA_HOME variable in your environment to match the 1>&2
echo location of your Java installation. 1>&2

goto fail

:findJavaFromJavaHome
set JAVA_HOME=%JAVA_HOME:"=%
set JAVA_EXE=%JAVA_HOME%/bin/java.exe

if exist "%JAVA_EXE%" goto execute

echo. 1>&2
echo ERROR: JAVA_HOME is set to an invalid directory: %JAVA_HOME% 1>&2
echo. 1>&2
echo Please set the JAVA_HOME variable in your environment to match the 1>&2
echo location of your Java installation. 1>&2

goto fail

:execute
@rem Setup the command line

set CLASSPATH=%APP_HOME%\gradle\wrapper\gradle-wrapper.jar


@rem Execute Gradle
"%JAVA_EXE%" %DEFAULT_JVM_OPTS% %JAVA_OPTS% %GRADLE_OPTS% "-Dorg.gradle.appname=%APP_BASE_NAME%" -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*

:end
@rem End local scope for the variables with windows NT shell
if %ERRORLEVEL% equ 0 goto mainEnd

:fail
rem Set variable GRADLE_EXIT_CONSOLE if you need the _script_ return code instead of
rem having the launcher return the cmd.exe error code.
set EXIT_CODE=%ERRORLEVEL%
if %EXIT_CODE% equ 0 set EXIT_CODE=1
if not ""=="%GRADLE_EXIT_CONSOLE%" exit %EXIT_CODE%
exit /b %EXIT_CODE%

:mainEnd
if "%OS%"=="Windows_NT" endlocal

:omega
