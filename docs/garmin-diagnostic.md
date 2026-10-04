# Garmin Instinct Solar diagnosis — 2026-09-28

## Result

The existing `com.cardio.diagnostic` app connected directly to the paired Instinct Solar on the Samsung S24 (SM-S921W, Android 14), subscribed to the standard Bluetooth Heart Rate Measurement characteristic, and received **30 notifications during a 20-second observation window**. Values were 64–65 BPM. This is a successful direct BLE heart-rate test, not a reading scraped from Garmin Connect's screen.

The user reported enabling live heart rate and broadcast during workouts. Garmin Connect was observed updating before the test and remained installed/running in the background while the diagnostic was in the foreground. The diagnostic disconnected automatically and Garmin Connect was returned to the foreground afterward. Pairing and watch settings were preserved.

## Technical observations

- Phone's connected bonded-device entry identified the peripheral as **Instinct Solar**.
- GATT connection, service discovery, and CCC descriptor subscription all returned success (`0`).
- Four services were discovered: GAP `1800`, GATT `1801`, Garmin vendor service `6a4e2800-667b-11e3-949a-0800200c9a66`, and Heart Rate `180D`.
- Heart Rate Measurement `2A37` supported notifications and had Client Characteristic Configuration descriptor `2902`.
- First BPM arrived about 0.7 seconds after starting the probe. Subscription completed about 0.3 seconds after start; the final notification arrived at 19.9 seconds.
- No watch firmware/model Device Information service was exposed, so firmware was not read during this test.
- No proprietary Garmin commands were needed. Only the standard notification subscription descriptor was written.

The full report is stored locally at `artifacts/garmin-ble-diagnostic.txt` (Git-ignored). Device addresses and serials are not embedded in source or this document. Repeat using the [diagnostic build/run instructions](ultrahuman-diagnostic.md#repeating-the-standard-service-probe), selecting the intended watch's address.

## What this establishes

The tested configuration supplies standard BLE BPM to an independent Android app on the S24 without an ANT+ adapter. It is a practical candidate for Cardio Lab's heart-rate panel, graph, and session average.

This does not yet establish:

- Which watch mode enables the feed: manual broadcasting, an active workout, or Garmin Connect's live mode were not isolated.
- Whether Garmin Connect can be absent/stopped, or whether initial pairing through it is required.
- Whether the Xiaomi treadmill phone can pair and receive the same stream.
- Connection stability, automatic broadcast activation, reconnect behavior, or accuracy throughout a workout.

Next integration work should subscribe to `180D/2A37`, timestamp readings, display stale/disconnected state when updates cease, and store valid HR samples with each session. Then verify automatic broadcasting during an actual workout on the intended treadmill phone.

## Documentation versus measurement

The original [Garmin Instinct Solar specifications](https://www.garmin.com.hk/products/intosports/instinct-solar-graphite/) describe ANT+ HR broadcasting. That documentation was insufficient to conclude this particular watch could not supply BLE heart rate. The successful direct notification test supersedes that earlier assumption for the tested configuration; it does not assert identical behavior for every Instinct Solar firmware or mode.
