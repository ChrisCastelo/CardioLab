# Idle serial capture, 2026-10-04

Read-only capture from the stock FitOS app (`com.ucare.fitos` 1.65.5, pid 954) on the
Echelon screen `545C101585`, connected to the treadmill through the original DB9 harness.
The belt stayed stopped. No frames were sent by us, logcat was not cleared, and
`/dev/ttyS3` was not opened by anything other than the stock app.

Raw logs and decoded JSONL stay local in `artifacts/treadmill-idle-20261004-151106/`
(ignored by Git): `baseline-logcat.txt` (existing buffer, 15:08:07–15:11:06),
`idle-serial-logcat.txt` (live, about 62 s), and their `*-decoded.jsonl`.

## Result

The controller replies. Every frame in both captures passed checksum validation
(553 baseline, 186 live, 0 errors). This is the first capture with receive frames.

FitOS logs each frame under both `SearialPortManager` (plain hex, `SNT`/`RCV`) and
`SerialCommManager` (sign-extended hex such as `FFFFFFF0`). The decoder's
`SearialPortManager` filter is correct. Full logcat dumps contain non-UTF-8 bytes from
other apps, so the decoder now decodes with replacement instead of failing.

## Connection handshake (FitOS, 15:08:07)

| Time | Dir | Frame | Decoded |
| --- | --- | --- | --- |
| 07.664 | tx | `F0A0010192` | A0 heartbeat, counter 1 |
| 07.865 | tx | `F0A10091` | A1 device info query |
| 07.886 | rx | `F0A10723DD00E70BFF018A` | model 35, hardware [221, 0], firmware [231, 11, 255], unit code 1 = **miles** |
| 08.065 | tx | `F0A30093` | A3 limits query |
| 08.085 | rx | `F0A3070C002EE001F400A9` | incline **0–12**, speed **0.5–12.0 mph** (wire ÷ 1000), countdown 0 |
| 08.666 | tx/rx | `F072010164` | opcode 72, payload `01`, echoed; meaning unknown |
| 09.066 | tx | `F0730063` | opcode 73 query |
| 09.099 | rx | `F07312…CC` | 18-byte ASCII string `STRIDE-8s22-…` (model/serial-like; suffix redacted) |

A3 was queried a second time at 08.865 with an identical reply.

## Idle steady state

- A0 heartbeat once per second from FitOS; the controller echoes the same frame back
  about 15 ms later (counter increments, wraps at one byte).
- D1 workout status about once per second, unsolicited, all zero while stopped:
  `F0D109000000000000000000CA`.
- No D0 (state/safety), D2 (incline), D3 (speed) or D4 (keys) frames were sent while
  idle. Those appear to be event-driven and need a deliberate change to observe.

## What this settles and what it doesn't

- The UI's 12 mph and 12-level ranges match the controller's reported maxima. The
  controller minimum is 0.5 mph, below the planned 2 mph start; 2 mph remains a
  product choice, not a hardware floor.
- Incline 0–12 is still in controller **levels**; the percent mapping is unknown.
- Model 35 and the A1 version bytes are recorded but not mapped to a product name;
  the opcode 73 string is the best identity evidence.
- Opcode 72 (payload `01`) is unresolved. FitOS also queries fan limits (A7); fan
  control is out of scope for CardioLab.
- D1 scaling for elapsed time, distance, calories and HR is still unconfirmed because
  every field is zero at idle.

## Safety-key test (15:16–15:17, belt stopped)

The user pulled and then reinserted the safety key while the live capture ran
(`artifacts/treadmill-safetykey-20261004-151639/`, local only). All frames passed
their checksums.

| Time | Dir | Frame | Decoded | FitOS state log |
| --- | --- | --- | --- | --- |
| 15:16:35.551 | rx | `F0D001AA6B` | D0 state `AA` = safety key not plugged | `SafeKeyNotPlugged` |
| 15:16:35.553 | tx | `F0D001AA6B` | FitOS echoes the D0 frame back | |
| 15:17:32.436 | rx | `F0D00100C1` | D0 state `00` = stop | `Stop` |
| 15:17:32.437 | tx | `F0D00100C1` | FitOS echoes the D0 frame back | |

- D0 is event-driven: one frame per change, nothing repeated while the key stays out.
- The stock app acknowledges each D0 by echoing it within about 2 ms. A CardioLab
  transport should do the same until shown otherwise.
- Heartbeats and zeroed D1 status continued at 1 Hz with the key out, so the link stays
  up and key removal is reported rather than silently dropping communication.
- Reinserting the key returns the controller to `stop`, not to any previous state.

## Supervised belt run (15:19–15:24)

The user started the belt with the physical Start key (FitOS requires a login, so no
app command was used), changed speed with the physical keys, then pressed physical
Stop. The resume/stop behaviour was explored until 15:28:32. FitOS sent nothing except heartbeats and echoes. Raw capture:
`artifacts/treadmill-run-20261004-152015/` (local only). 912 frames, all checksums valid.

| Time | Frame | Decoded | FitOS state log |
| --- | --- | --- | --- |
| 15:19:42.855 | `F0D00111D2` | D0 state `11` = start countdown (observed) | `Other` |
| 15:19:42.887 | `F0D30201F4BA` | D3 speed 500 = 0.5 mph | |
| 15:19:45.847 | `F0D00101C2` | D0 state `01` = start | `Start` |
| 15:21:55 – 15:22:12 | `F0D302…` | D3 1500, 2500, 3500, 4500, 5500 | |
| 15:22:31 – 15:22:32 | `F0D302…` | D3 4500, 3500, 2500 | |
| 15:24:09.259 | `F0D00102C3` | D0 state `02` = pause | `Pause` |
| 15:24:09.290 | `F0D3020000C5` | D3 speed 0 | |

Every D0 and D3 frame was echoed back by FitOS within a few ms.

Findings:

- State `11` lasts about 3 s before `start`; it is the start countdown. The controller
  begins at its 0.5 mph minimum.
- The physical speed keys step 1.0 mph per press (0.5, 1.5, 2.5 …). No D4 key frames
  were sent for Start, speed or Stop; the controller acts on its own keys and reports
  the resulting D0/D3 changes.
- The physical Start/Stop is one button, and its meaning depends on the press:
  - A short press while running gives **pause**: D0 `02` and D3 speed 0, and D1 freezes.
  - A short press while paused **resumes**. The controller repeats the countdown (`11`),
    restarts at 0.5 mph (`start`), and elapsed time and distance continue. The
    pre-pause speed is not restored.
  - A double press is pause followed by resume (0.3 s apart). A 3 s hold also resumes.
  - Holding for about 5 s **ends the workout**: pause, then D0 `00` (stop) 240 ms
    later, and D1 drops back to all zeros (15:28:32). This is the only full stop from
    the console. Removing and reinserting the safety key also ends in stop.
  - The button's LED shows the state: blinking red while paused, green once stopped
    (observed by the user).
- D1 layout confirmed: bytes 0–1 elapsed seconds (big-endian, passed 255 → `0100`),
  bytes 2–5 distance in 0.001 mile (at 2.5 mph: 27 counts in 38 s, expected 26.4),
  bytes 6–7 calories (reached 13), byte 8 heart rate (0, no chest strap).
- D3 speed is reported only on change, so CardioLab must remember the last D3 value
  rather than expect periodic speed frames.

## CardioLab owns the link (15:35–15:50)

- FitOS closes `/dev/ttyS3` whenever another app is in front (`unexpectedlyPaused`), and
  stops its heartbeats. In the background it keeps reopening and reconfiguring the port
  about once a second without transmitting.
- With FitOS released and nothing transmitting, a 30 s passive listen (and a safety-key
  pull) produced no bytes: the controller only reports while it receives heartbeats.
- Sending only the stock A0 heartbeat at 1 Hz made the controller resume D1 status at
  1 Hz immediately; 7 of 20 heartbeats were echoed (FitOS sees every echo; unexplained).
- CardioLab 0.6 (`TreadmillLink`) now owns the port while its console or video overlay is
  on screen: A0 heartbeat once a second, verbatim echoes of D0/D3, nothing else. It opens
  the port only after FitOS's last `SNT` log line is 3 s old, and releases it if FitOS
  transmits again. A supervised walk at 0.5 mph showed live state, speed and time.
- Link-loss test (15:53, supervised, belt running at 0.5 mph): CardioLab withheld the
  heartbeat from 15:53:21 to 15:53:36 while keeping the port open. The controller sent
  nothing at all during the gap, and the user reported the belt stopped. When heartbeats
  resumed, D1 was all zeros: the workout had ended, not paused. The exact delay before
  the belt stopped was not measured. The controller therefore fails safe on heartbeat
  loss, but CardioLab must not rely on that alone.

## On-screen controls (16:10–16:11, supervised)

CardioLab 0.7 sent each command once from its own buttons; every one was confirmed by the
controller. Raw link log: `artifacts/treadmill-controls-20261004/` (local only).

| Command | Sent | Controller reply |
| --- | --- | --- |
| Start | `F0B00101A2` | D0 `00` (current state), D0 `11` countdown, D3 0.5 mph, D0 `01` after 3 s |
| 3.0 mph | `F0B2020BBD6C` (3000 + 5) | D3 `0BB8` = 3000 within 35 ms |
| 2.0 mph | `F0B20207D580` (2000 + 5) | D3 `07D0` = 2000 |
| Pause | `F0B00102A3` | D0 `01` (current), D0 `02`, D3 0 |
| Resume | `F0B00101A2` | D0 `02` (current), countdown, 0.5 mph, D0 `01`; CardioLab then restored 2.0 mph |
| End | `F0B00100A1` | D0 `01` (current), D0 `02`, D3 0, D0 `00` about 1.2 s later |

- The controller acknowledges B0 by first repeating its current D0 state, then reporting
  the transition. End goes through pause before stop, like the 5 s button hold.
- B2 with the stock +5 is truncated to the requested value (3005 is reported as 3000).
- Second run (16:12–16:14) after the quick-start fix: Quick start sent B0 and, once the
  controller reported start, B2 2.0 mph automatically (confirmed by D3 2000). Incline B1
  levels 1, 2 and 0 were each confirmed by D2 within 30 ms (`F0D20101C4`, `F0D20102C5`,
  `F0D20100C3`). 4.0 mph (B2 4005) was confirmed as D3 4000, and End from 4 mph went
  through pause to stop in 2.5 s; the user reported the deceleration was not abrupt.

## Next justified test

Supervised: repeat the link-loss test at 3–4 mph to judge how abruptly the belt stops
when the heartbeat is lost (the 0.5 mph test could not show this).
