# Two-stage calibration (0.3)

## Workflow

1. With a securely fixed phone and the belt at test speed, record empty-belt noise. Nobody steps on the belt. A 3-second countdown warms the detector, followed by 6 measured seconds.
2. Keep phone placement and belt speed unchanged. Walk at a steady pace. A 5-second countdown allows settling; count both left and right foot contacts only during the following 30 seconds. Sound cues and visible countdowns mark the window. Enter 20–120 counted foot contacts.
3. Review reference count, old-threshold count, proposed count, empty-belt false count, and the proposed threshold range. Apply the result or keep the current setting.
4. Validate with a separate manually counted walk. Calibration accuracy on its own training recording does not establish workout accuracy. Recalibrate when placement or speed changes.

Calibration has no effect on workout time, steps, or HR averages. An existing session must be paused. Leaving the app cancels calibration. Calibration recordings stay in memory and are discarded on closing; applied threshold and reference/result summary persist in private preferences. Existing workout entries and the Garmin connection are preserved.

## Fitting method

`StepCalibration` replays timestamped raw XYZ samples with the same `StepDetector` used in workouts. Countdown samples warm the filters without counting. The production detector, 250 ms refractory period, and default 0.120 m/s² threshold are unchanged.

The noise floor is `max(0.025, 1.7 * p99(abs(filtered empty-belt signal)))`. Search 321 logarithmically spaced thresholds between this floor and slightly above the walking signal peak. Reject any candidate producing empty-belt steps; minimize absolute difference from the user's walking reference. Require error no greater than 5% (at least one step tolerance), then select the logarithmic midpoint of the widest contiguous equally good band. Reject bands narrower than three candidates. Display the result without applying it automatically.

Recordings require complete timed windows, at least one second of filter warmup, finite samples, ordered timestamps, and no sensor gaps exceeding 150 ms. Memory is bounded at 20,000 samples per stage. Fitting runs on a background executor and cancellation invalidates late results.

This tunes an amplitude threshold, not a personalized foot-strike classifier. Equal-sized echoes, weak alternating foot strikes, changing cadence, or poor separation from motor noise may be impossible to solve this way. The wizard reports failure instead of silently applying a bad fit. Aggregate count agreement can hide compensating misses and extra peaks, hence the independent validation walk.

## Automated checks

`test.ps1` includes production detector and workout/HR tests plus calibration cases: a synthetic echo trace that counts 90 events for 45 foot contacts at threshold 0.015 is fitted to 45; stationary walking samples, missing data, invalid acceleration, implausible references, and unattainable targets are rejected. These tests establish software behavior, not measured treadmill accuracy.

## S24 UI verification

Version 0.3 was built, linted (0 errors, 28 warnings), and installed on the Samsung S24. All detector, HR/workout, and calibration tests passed. The guided empty-belt stage, countdown, walking stage, reference entry and result screen were exercised on the device. A test reference of 60 without a matching treadmill walk was rejected; selecting Keep current setting retained the saved 0.015 manual threshold. No calibration was applied during this check. No AndroidRuntime or CardioLab errors appeared in the inspected recent log window. An accepted fit and its persistence still require a real counted treadmill walk on this phone.
