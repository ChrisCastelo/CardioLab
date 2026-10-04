#!/bin/sh
# macOS/Linux counterpart of test.ps1. Set JAVA_HOME to a JDK 17+.
set -e
cd "$(dirname "$0")"
J="${JAVA_HOME:?Set JAVA_HOME to a JDK 17 or newer}/bin"
O=artifacts/tests
S=app/src/main/java/com/cardio/lab
mkdir -p "$O"
run() { test_class=$1; shift; "$J/javac" -d "$O" "$@" "tests/$test_class.java"; "$J/java" -cp "$O" "$test_class"; }
run StepDetectorTest "$S/StepDetector.java"
run WorkoutTest "$S/Workout.java" "$S/HeartRatePacket.java"
run StepCalibrationTest "$S/StepDetector.java" "$S/StepCalibration.java"
run ConsoleSessionTest "$S/ConsoleSession.java"
run TreadmillStateTest "$S/TreadmillState.java"
python3 -m unittest discover -s tools/protocol -p 'test_*.py'
