# Smart Index

A private, offline Android app that indexes everything on your phone (photos, videos, documents, other files,
the call log, Samsung call recordings, and every SMS/MMS) so you can search it in one place by name, folder,
contact, phone number, message text and date, and browse a timeline of any day.

It is an index only: results open in the apps you already use. **The app has no internet permission and the
index never leaves the device.**

## Features

- **One search box for everything.** Matches file names, folders, contact names, phone numbers and SMS/MMS text.
  A query matches as a contiguous, case-insensitive substring of any field, so `view` finds "preview" but
  `sea view` does not find "Sea_View". Results are highlighted and update as you type.
- **Day timeline.** Pick a day (or a range) to see everything that happened on it, grouped by part of the day.
  Combine a date with a search to narrow results.
- **Type tabs.** Filter by photos, videos, documents, other files, calls, messages and recordings, each with a
  result count.
- **Calls and recordings together.** Samsung call recordings are matched to the call they belong to, so you can
  play a recording from the call entry, with a docked mini-player (pause, seek ±10 s).
- **Item actions.** Open a file, open it with another app, message or call back a number, open or add a contact.
- **Group MMS.** A group message is searchable by the saved name of every participant.
- **Always fresh.** Changes are picked up in the background (WorkManager, content triggers and a 15-minute
  periodic pass) and on app open. Index status shows per-type counts and lets you re-index.
- **Permissions on your terms.** Onboarding asks for one access group at a time. A type is indexed only while
  its group is granted, and its data is purged when you revoke it.
- **Light and dark themes**, large-font and TalkBack-friendly layouts.

### Not in this version

- Document **contents** are not searched. Documents are found by name, folder, type, dates and size.
- Call-recording matching targets Samsung's recording file naming. Other vendors' recordings are listed as
  files but may not be linked to calls.

## Privacy

- No `INTERNET` or other network permission in any build variant. The build fails if a dependency adds one.
- `allowBackup=false`; cloud backup and device transfer are excluded. The index lives in app-private storage.
- No analytics, crash reporting or ad SDKs. Logs never contain message text, numbers, names or file names.

## Tech stack

Kotlin · Jetpack Compose + Material 3 · Hilt · Room on the AndroidX bundled SQLite (FTS5 trigram index for
substring search) · WorkManager · MediaStore, CallLog, Telephony and Contacts providers · Coil 3 (no network
artifact) · Media3 ExoPlayer · minSdk 33 (Android 13), compile/target SDK 36 · Java 17 bytecode.
Exact versions are in [`gradle/libs.versions.toml`](gradle/libs.versions.toml).

How search works is described in [`docs/architecture.md`](docs/architecture.md).

## Repository layout

```text
app/            :app — Application, MainActivity, navigation, DI, manifest
core/           :core:model · :core:designsystem · :core:permissions · :core:index (store + search)
source/         :source:media · :source:telephony · :source:recordings
sync/           :sync — indexing orchestration (WorkManager)
feature/        :feature:home · :feature:player · :feature:onboarding · :feature:status
benchmark/      :benchmark — Macrobenchmark + baseline profile
tools/seeder/   :tools:seeder — debug-only test-data seeder APK
testdata/       small test-file set (see testdata/README.md)
build-logic/    convention plugins + privacy and module-graph checks
config/lint/    lint policy (Security and Correctness are errors)
docs/           architecture notes and performance/privacy procedures
```

Dependency direction, enforced by `./gradlew verifyModuleGraph` (part of `check`): `feature/*` → `core/*`
(+ `:feature:player`); `sync` → `source/*` → `core/index` → `core/model`; `:app` wires everything. No feature
depends on a source, and sources never depend on each other.

## Build, run and test

Requirements: JDK 17 or newer to run Gradle (the Android Studio bundled JDK works), the Android SDK with
platform 36 and build-tools 36.x, and a device or emulator on Android 13+ (the recording matcher targets
Samsung One UI 6+). Gradle 9.5.1 comes from the wrapper.

```bash
export JAVA_HOME=/path/to/jdk                      # e.g. the Android Studio bundled JBR
export ANDROID_HOME="$HOME/Android/Sdk"            # or sdk.dir in local.properties (not committed)

./gradlew spotlessApply                              # format (ktlint via Spotless)
./gradlew spotlessCheck lint test                    # quick gate: format, lint, JVM unit + Robolectric tests
./gradlew check                                      # full gate: the above + privacy and module-graph checks
./gradlew :app:assembleDebug :app:installDebug       # build and install the debug app
./gradlew :app:assembleRelease                       # release APK (see signing below)
./gradlew connectedCheck                             # instrumented tests on the attached device
adb shell pm clear com.kuriousdragon.smartindex      # reset to first run
```

On a machine with little RAM add `--max-workers=2`.

### Release signing

Put these four keys in `~/.gradle/gradle.properties` (never in the repo) or pass them with `-P`:

```properties
SMARTINDEX_KEYSTORE_FILE=/absolute/path/to/smartindex.jks
SMARTINDEX_KEYSTORE_PASSWORD=...
SMARTINDEX_KEY_ALIAS=...
SMARTINDEX_KEY_PASSWORD=...
```

When all four are set, `:app:assembleRelease` produces a signed `app-release.apk`; otherwise it produces
`app-release-unsigned.apk` and prints "release signing not configured".

### What `check` enforces

The two verify tasks also run before `package<Variant>` / `bundle<Variant>`, so `assemble*` and `bundle*` fail
on a violation too.

| Task | Fails when |
|---|---|
| `verifyNoNetworkPermissions` (`:app`, `:tools:seeder`, every variant) | a merged manifest requests INTERNET, ACCESS_NETWORK_STATE or another network permission |
| `verifyManifestHardening` (same) | `allowBackup` is not false, `dataExtractionRules` is missing, or a component other than the launcher activity is exported (system-guarded ones such as WorkManager's `SystemJobService` are allowed) |
| `verifyModuleGraph` (root) | a module depends on one the layout above forbids |
| `:build-logic:convention:test` | the checks above lose their own tests |
| `spotlessCheck` | formatting differs from ktlint (`.editorconfig`) |

Continuous integration: `.gitea/workflows/check.yml` runs the same gate when a runner is available and the
repository variable `SIDX_CI_ENABLED` is `true`. Otherwise run `./gradlew check` locally before merging.

## Test and benchmark harness

A debug-only seeder (`:tools:seeder`) fills a device with deterministic synthetic data, and a Macrobenchmark
module (`:benchmark`) measures search and timeline latency. Step-by-step procedures, including battery and
network-privacy measurements, are in [`docs/perf-procedures.md`](docs/perf-procedures.md).

```bash
./gradlew :tools:seeder:installDebug
P=com.kuriousdragon.smartindex.seeder
adb shell pm grant $P android.permission.READ_CALL_LOG; adb shell pm grant $P android.permission.WRITE_CALL_LOG
adb shell pm grant $P android.permission.READ_CONTACTS;  adb shell pm grant $P android.permission.WRITE_CONTACTS
adb shell pm grant $P android.permission.POST_NOTIFICATIONS
adb shell appops set --uid $P MANAGE_EXTERNAL_STORAGE allow

SEEDER=com.kuriousdragon.smartindex.seeder/.SeedActivity
adb shell am start -n $SEEDER --es cmd seed --es profile small --el seed 42   # profiles: small (smoke), nfr002 (full scale), edge
adb shell am start -n $SEEDER --es cmd status                                   # counts + SMS role state
adb shell am start -n $SEEDER --es cmd wipe                                     # remove everything the seeder wrote
adb logcat -s SiSeed                                                            # progress
```

Push the committed test files with the commands in [`testdata/README.md`](testdata/README.md).

Run a benchmark (keep the app installed after the run, otherwise Gradle uninstalls it together with the index):

```bash
./gradlew :benchmark:connectedBenchmarkAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.kuriousdragon.smartindex.benchmark.S1ShortQueryBenchmark \
  -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true
python3 scripts/nfr003_report.py benchmark/build/outputs/connected_android_test_additional_output --out nfr003-report.md
```

Search-latency budget: first page of results in under 1,000 ms at p95. **Use a real device** for this gate;
emulator numbers are only a harness smoke test (on an emulator also pass
`-Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.suppressErrors=EMULATOR`).

The report exits 0 when every gated p95 is below 1,000 ms, 1 otherwise, and 2 when no result JSON was found.
Script tests: `python3 -m unittest scripts/test_nfr003_report.py`.

### Baseline profile

Regenerate with `./gradlew :app:generateBaselineProfile` on a seeded, indexed real device (an API 33+ emulator
with the `small` profile also works); it writes `app/src/main/generated/baselineProfiles/`. Do it whenever Home
or Search change, and before each release. Never hand-edit or commit an empty profile.

## Known limitations

- A rare one- or two-character query is a bounded full scan of the index by design. On very large indexes
  (hundreds of thousands of items) the first page can take a few seconds on a cold cache; results appear with a
  "Searching…" state. This has not been measured on a physical device yet; see `docs/architecture.md`.
- Real-device checks are still open: the 1,000 ms latency gate, the 24-hour battery measurement, TalkBack
  walkthroughs and the Samsung recording file-name capture.
- Samsung battery management can delay background indexing; opening the app always catches up immediately.

## Contributing

Run `./gradlew spotlessApply` and `./gradlew check` before sending a change; `main` must always build and pass
its tests. Keep the privacy invariants above intact: a change that adds a network permission, a tracking SDK or
sensitive logging will not be accepted.

## License

Released under the [MIT License](LICENSE).
