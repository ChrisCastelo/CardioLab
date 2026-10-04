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

## Next justified test

A no-motion safety-key test: with the user present and the belt stopped, remove and
reinsert the safety key while recording the same live capture. This should produce D0
state frames without any risk of belt movement. After that, the supervised Start/Stop
stage (user at the console, ready on the physical Stop) to observe D0/D3/D4 and
non-zero D1 fields at the lowest speed.
