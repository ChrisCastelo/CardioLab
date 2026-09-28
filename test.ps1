$ErrorActionPreference = 'Stop'
$taskJava = 'C:\Program Files\Android\Android Studio\jbr\bin'
New-Item -ItemType Directory -Force "$PSScriptRoot\artifacts\tests" | Out-Null
& "$taskJava\javac.exe" -d "$PSScriptRoot\artifacts\tests" "$PSScriptRoot\app\src\main\java\com\cardio\lab\StepDetector.java" "$PSScriptRoot\tests\StepDetectorTest.java"
if ($LASTEXITCODE -ne 0) { throw 'Test compilation failed' }
& "$taskJava\java.exe" -cp "$PSScriptRoot\artifacts\tests" StepDetectorTest
if ($LASTEXITCODE -ne 0) { throw 'Detector tests failed' }
