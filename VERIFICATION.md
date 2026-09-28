# Verification — 2026-09-28

- Debug APK built successfully with the included Gradle wrapper.
- Android lint completed: 0 errors, 7 warnings (older target/tool version and English-only text formatting). Target 34 was selected for the connected Android 14 test phone.
- Standalone detector checks passed: stationary noise, sub-threshold vibration, 72/120/180/210 steps-per-minute synthetic signals, irregular timestamps, axis orientation, preview/calibration exclusion, restart settling, invalid data, refractory spacing, reset.
- Installed successfully on connected Android 14 device, model 2311DRK48G.
- Launched `com.cardio.lab/.MainActivity`; verified its foreground window, UI hierarchy and screenshot.
- Live preview displayed approximately 103 Hz from the ST lsm6dsoq_acc sensor.
- Device security rejected ADB input injection. No device settings were changed to bypass this. Button interactions, calibration, screen-awake duration, pause/resume and CSV export still need a hands-on check on the phone.
- Treadmill step accuracy has **not** been measured. Synthetic test success does not establish real-world accuracy.

Local screenshot from the installed app: `artifacts/cardio-screen.png`.
First physical experiment: follow the empty-belt and manually counted 100-step protocol in README.md.
