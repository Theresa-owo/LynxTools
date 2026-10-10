param([Parameter(ValueFromRemainingArguments=$true)][string[]]$GradleArgs)
$ErrorActionPreference = 'Stop'
$java = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin/java.exe' } else { 'java.exe' }
& $java '-classpath' "$PSScriptRoot/gradle/wrapper/gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain @GradleArgs
exit $LASTEXITCODE
