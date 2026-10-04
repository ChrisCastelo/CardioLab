$ErrorActionPreference = 'Stop'
$taskJava = 'C:\Program Files\Android\Android Studio\jbr\bin'
New-Item -ItemType Directory -Force "$PSScriptRoot\artifacts\tests" | Out-Null
& "$taskJava\javac.exe" -d "$PSScriptRoot\artifacts\tests" "$PSScriptRoot\app\src\main\java\com\cardio\lab\StepDetector.java" "$PSScriptRoot\tests\StepDetectorTest.java"
if ($LASTEXITCODE -ne 0) { throw 'Test compilation failed' }
& "$taskJava\java.exe" -cp "$PSScriptRoot\artifacts\tests" StepDetectorTest
if ($LASTEXITCODE -ne 0) { throw 'Detector tests failed' }
& "$taskJava\javac.exe" -d "$PSScriptRoot\artifacts\tests" "$PSScriptRoot\app\src\main\java\com\cardio\lab\Workout.java" "$PSScriptRoot\app\src\main\java\com\cardio\lab\HeartRatePacket.java" "$PSScriptRoot\tests\WorkoutTest.java"
if ($LASTEXITCODE -ne 0) { throw 'Workout test compilation failed' }
& "$taskJava\java.exe" -cp "$PSScriptRoot\artifacts\tests" WorkoutTest
if ($LASTEXITCODE -ne 0) { throw 'Workout tests failed' }
& "$taskJava\javac.exe" -d "$PSScriptRoot\artifacts\tests" "$PSScriptRoot\app\src\main\java\com\cardio\lab\StepDetector.java" "$PSScriptRoot\app\src\main\java\com\cardio\lab\StepCalibration.java" "$PSScriptRoot\tests\StepCalibrationTest.java"
if ($LASTEXITCODE -ne 0) { throw 'Calibration test compilation failed' }
& "$taskJava\java.exe" -cp "$PSScriptRoot\artifacts\tests" StepCalibrationTest
if ($LASTEXITCODE -ne 0) { throw 'Calibration tests failed' }
& "$taskJava\javac.exe" -d "$PSScriptRoot\artifacts\tests" "$PSScriptRoot\app\src\main\java\com\cardio\lab\ConsoleSession.java" "$PSScriptRoot\tests\ConsoleSessionTest.java"
if ($LASTEXITCODE -ne 0) { throw 'Console test compilation failed' }
& "$taskJava\java.exe" -cp "$PSScriptRoot\artifacts\tests" ConsoleSessionTest
if ($LASTEXITCODE -ne 0) { throw 'Console tests failed' }
& "$taskJava\javac.exe" -d "$PSScriptRoot\artifacts\tests" "$PSScriptRoot\app\src\main\java\com\cardio\lab\TreadmillState.java" "$PSScriptRoot\tests\TreadmillStateTest.java"
if ($LASTEXITCODE -ne 0) { throw 'Treadmill state test compilation failed' }
& "$taskJava\java.exe" -cp "$PSScriptRoot\artifacts\tests" TreadmillStateTest
if ($LASTEXITCODE -ne 0) { throw 'Treadmill state tests failed' }
