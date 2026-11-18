# Performance & privacy procedures

Placeholders such as `<pkg>`, `<UID>`, `<NNN>`, tokens and dates are filled in by whoever runs the procedure.

| Section | Covers |
|---|---|
| 1 Measurement rules | all benchmark runs |
| 2 Seeding, search latency, initial index time | latency budget, 5-minute initial index |
| 3 Freshness per type | new and deleted items show up within 15 minutes |
| 4 Privacy and 24 h battery | no network traffic, no backup, under 3 % battery per day |
| 5 Recordings field capture | real Samsung recording file names |

Targets used below: search first page p95 under 1,000 ms; initial index of the full-scale profile within
5 minutes; new items searchable within 15 minutes; under 3 % battery over 24 h; zero network bytes.

> **Real device only.** The search-latency budget gate (steps 10–11 of section 2) must use benchmark JSON pulled from a real device. Emulator numbers (e.g. the `sdk_gphone64_x86_64` S1 run) are valid only as a smoke test of the harness and must never be reported against the 1,000 ms budget.

### 1. Benchmark measurement rules

Seeded `nfr002` profile, initial index finished; `benchmarkRelease` build; battery ≥ 50 % (USB charging during Macrobenchmark runs is unavoidable; note it in the report), battery saver off, brightness fixed, `adb shell dumpsys thermalservice` status 0 before and after (a run that ends throttled is discarded and repeated after 10 min); ≥ 10 iterations (we use 20/15); evidence = JSON + the report + the Perfetto trace of the worst iteration (`benchmark/build/outputs/…/*.perfetto-trace`), device model, One UI/Android build, `main@<sha>`, seed and profile.

### 2. Seeding, search-latency benchmark and initial index time


 1. Dedicated test phone, developer options on, USB debugging on, screen timeout 30 min, "Stay awake" on.
 2. `./gradlew :tools:seeder:installDebug`, then the grant commands from the README.
 3. `adb shell am start -n com.kuriousdragon.smartindex.seeder/.SeedActivity --es cmd seed --es profile nfr002 --el seed 42`; accept "Set as default SMS app" (or use `adb shell cmd role add-role-holder --user 0 android.app.role.SMS com.kuriousdragon.smartindex.seeder`).
 4. Watch `adb logcat -s SiSeed`. At `role release-now previous=<pkg>`, run `adb shell cmd role add-role-holder --user 0 android.app.role.SMS <pkg>` (or tap the notification and pick the previous app). At `run-end`, pull the seed manifest, check `failed` is 0, and check `role held=false` via `cmd status`.
 5. Build and install the app under test: `./gradlew :app:installBenchmarkRelease` (fresh: `adb shell pm clear com.kuriousdragon.smartindex` first).
 6. `adb logcat -c && adb logcat -v epoch -s SiPerf | tee nfr004-run1.log`.
 7. Open Smart Index, complete onboarding granting all five groups, start a stopwatch and tap **Start indexing**.
 8. Wait for `run end mode=FULL durationMs=<n> …`. **The initial-index check passes** when `durationMs ≤ 300000` (and `run start` was ≤ 2 s after the tap) and the per-type counts equal the manifest's `written` minus `expectedExcluded`. While it runs, scroll Home and search "hotel" (the app must stay responsive). Repeat 3 times (`pm clear` → onboarding → Start indexing); all 3 must pass.
 9. Busy-day scroll during indexing: on Index status tap Re-index everything › Re-index and immediately run `./gradlew :benchmark:connectedBenchmarkAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.kuriousdragon.smartindex.benchmark.S4TimelineBenchmark#scrollBusyDay -Pandroid.testInstrumentationRunnerArguments.sidx.allowIndexing=true -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true`.
10. Search latency: after the index is idle, **S1 first**: `./gradlew :benchmark:connectedBenchmarkAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.kuriousdragon.smartindex.benchmark.S1ShortQueryBenchmark -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true`, then S6, S2, S3, S4 (`-Pandroid.testInstrumentationRunnerArguments.sidx.busyDaysBack=<today − busyDay>`), S5. `leaveApksInstalledAfterRun=true` is mandatory: otherwise Gradle uninstalls the app and its index after the run.
11. `python3 scripts/nfr003_report.py benchmark/build/outputs/connected_android_test_additional_output --out nfr003-report.md`; exit code 0 = every gated p95 < 1,000 ms. Attach the report, the JSON and the worst trace. If S1's `qz` p95 is 1 s or more, the rare short-query path needs attention (see `docs/architecture.md`, performance notes).

### 3. Freshness per type (debug build)


1. `./gradlew :app:installDebug` (index finished), then `adb logcat -c && adb logcat -v epoch | grep -E "SiSeed|run start|source |match " | tee nfr005.log` (debug log lines: `run start reason=<R> … sinceLastRunMs=<n>`, `source <SRC> mode=INCREMENTAL written=<w> skipped=<s> deleted=<d> ms=<t>`).
2. Press Home; do **not** open Smart Index until the step ends.
3. Create one item per type at T0 (note the `fresh … createdAtMs=` line or the clock): SMS — send from a second phone (real) or `--es cmd fresh --es type SMS --es token F01` (needs the SMS role, see the seeder commands in the README); call — a real call; photo — camera or `fresh type=PHOTO token=F02`; video — `fresh type=VIDEO token=F03`; document — `fresh type=DOCUMENT token=F04` (writes `Download/fresh-F04.pdf`); other file — `fresh type=OTHER token=F05` (`Download/fresh-F05.zip`); recording — a real recorded call on the SIM. Seeder `fresh` items carry the token in their name or body.
4. T_indexed = the first `source <SRC> … written>=1` line after T0 for that source (MEDIA_STORE, SMS, MMS, CALL_LOG); confirm by pulling the DB: `adb exec-out run-as com.kuriousdragon.smartindex cat databases/index.db > index.db; adb exec-out run-as com.kuriousdragon.smartindex cat databases/index.db-wal > index.db-wal; sqlite3 index.db "SELECT id, primary_ts FROM item WHERE title LIKE '%F04%' OR body LIKE '%F01%'"`.
5. Pass: T_indexed − T0 ≤ 15 min per type. Record every `sinceLastRunMs`; large values point at OEM battery management delaying background work.
6. Deletions: delete the items at T1 (Gallery/My Files/Messages/Phone, or `cmd wipe` for seeder items); pass when the next run logs `deleted>=1` for the source and the DB query returns nothing, ≤ 15 min.
7. On-open: repeat step 3 with new tokens and open the app at once; the items must be searchable as soon as the silent catch-up ends.

### 4. Privacy and 24 h battery (release build)


1. `./gradlew :app:assembleRelease`; `aapt2 dump permissions app/build/outputs/apk/release/*.apk` → no `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE` or other network permission (same for the debug and benchmarkRelease APKs). Install the release APK, onboard, index.
2. UID: `adb shell dumpsys package com.kuriousdragon.smartindex | grep userId=`. Snapshot `adb shell dumpsys netstats detail > netstats-before.txt`.
3. Charge to 100 %, then `adb shell dumpsys batterystats --reset` and **unplug** (use wireless debugging, or reconnect only at the end). Default battery settings (do not set Unrestricted).
4. 24 h normal use with the app mostly in background: ≥ 10 SMS, ≥ 5 calls (2 recorded), ≥ 10 photos, 2 downloads, open the app 5 times, search, play a recording.
5. Plug in; `adb shell dumpsys batterystats --charged com.kuriousdragon.smartindex > batterystats.txt`; `adb bugreport bugreport.zip` → load it into a locally run Battery Historian for wakelocks and job runs; `adb shell dumpsys netstats detail > netstats-after.txt`.
6. **The battery check passes** when the app's "Estimated power use" (`Uid u0a<NNN>: <mAh>` in `batterystats.txt`) ÷ battery capacity (`Capacity:` line) < 3 %, cross-checked with Settings › Battery › app usage (screenshot). Also record JobScheduler runs (`adb shell dumpsys jobscheduler | grep -c com.kuriousdragon.smartindex`) and wakelock time.
7. **Network:** for the app UID, rx/tx bytes in `netstats-after` − `netstats-before` = 0 on every interface.
8. Backup: `adb shell bmgr backupnow com.kuriousdragon.smartindex` reports nothing backed up; merged manifest `allowBackup=false`.
9. No document contents in the index (debug build with the `small` profile and `testdata/` pushed): pull `index.db` + `-wal` (as §6.7); `sqlite3 index.db "SELECT COUNT(*) FROM item WHERE type = 3 AND body IS NOT NULL"` = 0; `grep -a -c "booking reference" index.db index.db-wal` = 0 for both files.

### 5. Recordings field capture

The matcher's real-name capture needs a real Samsung phone: record a few calls, list the file names under `Recordings/Call/`, and compare them with the patterns in `source/recordings` (`RecordingNameParser`). Add any new real-world shape (with personal data removed) to `testdata/recordings/sample-names.tsv`, and update the seeder's name mix to match.

## Evidence checklist

- [ ] Device model, One UI / Android build, `main@<sha>`, seed and profile
- [ ] Battery ≥ 50 %, battery saver off, `dumpsys thermalservice` status 0 before and after
- [ ] `nfr003-report.md` + benchmark JSON + worst-iteration Perfetto trace
- [ ] `nfr004-run1.log` … `nfr004-run3.log` (3 passing runs)
- [ ] `nfr005.log` and the DB query output
- [ ] `aapt2 dump permissions` output for release, debug and benchmarkRelease APKs
- [ ] `netstats-before.txt`, `netstats-after.txt`, `batterystats.txt`, `bugreport.zip`, battery screenshot
- [ ] Seed manifest (`seed-manifest.json`) with `failed` = 0
