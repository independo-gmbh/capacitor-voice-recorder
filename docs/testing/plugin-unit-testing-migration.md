# Title: Testable Capacitor Plugin Architecture + Unit Testing Migration
- Status: Ready to implement
- Owner(s): TBD
- Last updated: 2026-01-09
- Scope: Capacitor VoiceRecorder plugin (web + Android + iOS)

## 1) Goal, Non-goals, and Compatibility Promise
- Goal: adopt a testable 3-layer architecture and add reliable unit tests + CI coverage across web, Android, and iOS.
- Non-goals: emulator/simulator UI tests and end-to-end recording tests are out of scope for this phase.
- Compatibility promise: no breaking public API changes if possible; keep public API backwards compatible.

## 2) Current Public API Surface (Baseline)
- Defined in `src/definitions.ts` and exposed via `src/index.ts`.
- Methods (signatures unchanged):
  - `canDeviceVoiceRecord(): Promise<{ value: boolean }>`
  - `requestAudioRecordingPermission(): Promise<{ value: boolean }>`
  - `hasAudioRecordingPermission(): Promise<{ value: boolean }>`
  - `startRecording(options?: { directory?: Directory; subDirectory?: string }): Promise<{ value: boolean }>`
  - `stopRecording(): Promise<{ value: { recordDataBase64: string; msDuration: number; mimeType: string; uri?: string } }>`
  - `pauseRecording(): Promise<{ value: boolean }>`
  - `resumeRecording(): Promise<{ value: boolean }>`
  - `getCurrentStatus(): Promise<{ status: 'RECORDING' | 'PAUSED' | 'INTERRUPTED' | 'NONE' }>`
  - `addListener('voiceRecordingInterrupted' | 'voiceRecordingInterruptionEnded', ...): Promise<PluginListenerHandle>`
  - `removeAllListeners(): Promise<void>`
- Error codes consumers may rely on (as message strings from `reject`):
  - Web: `MISSING_PERMISSION`, `ALREADY_RECORDING`, `MICROPHONE_BEING_USED`, `DEVICE_CANNOT_VOICE_RECORD`, `FAILED_TO_RECORD`, `EMPTY_RECORDING`, `RECORDING_HAS_NOT_STARTED`, `FAILED_TO_FETCH_RECORDING`, `COULD_NOT_QUERY_PERMISSION_STATUS`
  - Android: `MISSING_PERMISSION`, `ALREADY_RECORDING`, `MICROPHONE_BEING_USED`, `CANNOT_RECORD_ON_THIS_PHONE`, `FAILED_TO_RECORD`, `EMPTY_RECORDING`, `RECORDING_HAS_NOT_STARTED`, `FAILED_TO_FETCH_RECORDING`, `NOT_SUPPORTED_OS_VERSION`
  - iOS: `MISSING_PERMISSION`, `ALREADY_RECORDING`, `MICROPHONE_BEING_USED`, `CANNOT_RECORD_ON_THIS_PHONE`, `FAILED_TO_RECORD`, `EMPTY_RECORDING`, `RECORDING_HAS_NOT_STARTED`, `FAILED_TO_FETCH_RECORDING`, `FAILED_TO_MERGE_RECORDING`

## 3) Target Architecture (Design for Testability)
### Layering model (3-layer split + bridge wrapper)
1) **Core/Domain**: pure logic and internal contract types; no Capacitor or platform imports.
2) **Service**: orchestrates flows and error mapping; depends on adapters/interfaces.
3) **Adapters + Platform Impl**: wrap platform APIs (MediaRecorder/AVFoundation/Android APIs).
4) **Capacitor Bridge**: thin wrapper, parse input → call service → resolve/reject (boundary layer, not part of the core 3-layer split).

### Before vs after layout
```text
Before (selected)
src/web.ts
src/VoiceRecorderImpl.ts
android/.../VoiceRecorder.java
android/.../CustomMediaRecorder.java
ios/Sources/VoiceRecorder/VoiceRecorder.swift
ios/Sources/VoiceRecorder/CustomMediaRecorder.swift

After (selected)
src/web.ts                           (bridge)
src/service/VoiceRecorderService.ts  (service)
src/adapters/MediaRecorderAdapter.ts (web adapter)
src/adapters/FilesystemAdapter.ts    (web adapter)
src/core/recording-contract.ts       (internal types + mapping)

android/src/main/java/app/independo/capacitorvoicerecorder/VoiceRecorder.java             (bridge)
android/src/main/java/app/independo/capacitorvoicerecorder/service/VoiceRecorderService.java
android/src/main/java/app/independo/capacitorvoicerecorder/adapters/RecorderPlatform.java
android/src/main/java/app/independo/capacitorvoicerecorder/platform/CustomMediaRecorder.java
android/src/main/java/app/independo/capacitorvoicerecorder/core/RecordData.java

ios/Sources/VoiceRecorder/Bridge/VoiceRecorder.swift          (bridge)
ios/Sources/VoiceRecorder/Service/VoiceRecorderService.swift  (service)
ios/Sources/VoiceRecorder/Adapters/RecorderPlatform.swift
ios/Sources/VoiceRecorder/Platform/CustomMediaRecorder.swift  (platform impl)
ios/Sources/VoiceRecorder/Core/RecordData.swift
```

### Naming conventions
- Services: `*Service` (e.g., `VoiceRecorderService`)
- Adapters: `*Adapter` (e.g., `MediaRecorderAdapter`, `AudioSessionAdapter`)
- Platform implementations: `*Impl` or platform-specific names (e.g., `CustomMediaRecorder`)

### Rules of separation
- Bridge wrappers must not contain business logic, platform API calls, or file IO.
- Services own orchestration, error mapping, and contract normalization.
- Adapters are the only place platform APIs are called.
- Core/domain types are pure and used by all platforms for consistent mapping.
- Bridge wrappers may call Capacitor permission APIs and `notifyListeners`, but should not do file IO or platform work.
- Services should not call `notifyListeners`; they signal via callbacks/events to the bridge.

### Capacitor best practices and performance constraints
- Native errors use `call.reject(legacyMessage, canonicalCode, ...)` to preserve legacy messages while providing a stable code.
- File IO and duration calculations must run off the main thread; only `resolve/reject` on main.
- Avoid double-reading audio files; compute duration and base64 using a single read path when possible.
- Do not base64-encode when returning a file `uri`.

## 4) Compatibility Strategy: Boundary Adapters & Tolerant Parsing
### Boundary adapters
- Keep `src/definitions.ts` as the external contract.
- Introduce internal models (core types) used by services.
- Use **input adapters** to accept and normalize current inputs without changing public signatures.
- Use **output adapters** to emit current response shapes (including null vs empty string behavior).
- Use **error adapters** to map internal error codes to current platform-specific error strings.

### Compatibility modes (legacy vs normalized)
- Use Capacitor plugin config to select response format:
  - `plugins.VoiceRecorder.responseFormat = 'legacy' | 'normalized'`
  - Default is `legacy` in the current major.
- `normalized` mode emits the vNext contract (see Section 5) and canonical error codes.
- Next major: `normalized` becomes default and `legacy` mode is removed.

### Allowed changes without breaking
- Adding optional fields is OK.
- Accepting additional input shapes is OK.
- Changing existing field meaning/type is NOT OK.

### Cross-platform drift handling
- Choose a canonical internal model and normalize internally.
- Preserve external behavior per platform by default through output adapters.
- If a platform currently differs (e.g., null vs empty string), keep it in default mode and document it.
- When `responseFormat = 'normalized'`, adapters must emit the same external behavior on all platforms.

## 5) Contract: Payload Shape + Error Codes (Single Source of Truth)
### Internal canonical contract (service)
- Internal type: `{ recordDataBase64?: string | null; uri?: string | null; msDuration: number; mimeType: string }`.
- Exactly one of `recordDataBase64` or `uri` is expected to be populated.
- `msDuration` is milliseconds, non-negative.
- `mimeType` must match platform output rules.

### Legacy external shape rules (current major, default)
- Always resolve `stopRecording()` to `{ value: { recordDataBase64, msDuration, mimeType, uri } }`.
- Preserve current platform behavior:
  - Web returns empty string for `recordDataBase64` when `uri` is used.
  - iOS returns empty string for `recordDataBase64` and `uri` when not present.
  - Android may return null for `recordDataBase64` when `uri` is used.
- Output adapters must keep this behavior when `responseFormat = 'legacy'`.

### vNext external contract (normalized mode + next major default)
- `recordDataBase64` and `uri` are optional and omitted when absent; empty strings are not used.
- Exactly one of `recordDataBase64` or `uri` is set.
- `uri` is a valid URI string (for native: `file://...`).
- `recordDataBase64` is raw base64 without a `data:` prefix.
- Canonical error codes (used across all platforms):
  - `MISSING_PERMISSION`, `ALREADY_RECORDING`, `MICROPHONE_BEING_USED`, `DEVICE_CANNOT_VOICE_RECORD`,
    `FAILED_TO_RECORD`, `EMPTY_RECORDING`, `RECORDING_HAS_NOT_STARTED`, `FAILED_TO_FETCH_RECORDING`,
    `FAILED_TO_MERGE_RECORDING`, `NOT_SUPPORTED_OS_VERSION`, `COULD_NOT_QUERY_PERMISSION_STATUS`
- Deprecate `CANNOT_RECORD_ON_THIS_PHONE` in favor of `DEVICE_CANNOT_VOICE_RECORD`.

### Error code mapping rules (legacy + normalized)
- Internal errors are canonical.
- Legacy mode uses platform-specific message strings:
  - Web: message `DEVICE_CANNOT_VOICE_RECORD`
  - Android/iOS: message `CANNOT_RECORD_ON_THIS_PHONE`
- Native always provides canonical `code` in `call.reject(...)` while keeping legacy `message`.
- Web throws `Error` with legacy `message`; in normalized mode, attach `error.code = canonicalCode`.

### Contract tests
- Define test vectors from `src/definitions.ts` and current platform behavior.
- Validate that each platform emits the expected external shape and error codes.
- Include tests that lock backward-compatible behavior (null/empty-string conventions and error mapping).
- Add normalized-mode tests to enforce vNext consistency without changing defaults.

## 6) Testing Strategy (What We Test Where)
| Platform | Target | How | Notes |
| --- | --- | --- | --- |
| Web | Service + core | Jest + ts-jest, mock browser globals | Avoid `registerPlugin()` proxy in tests |
| Android | Service | JVM unit tests with fakes | Avoid emulator, test adapters with fakes |
| iOS | Service | XCTest via `swift test` | Prefer SPM; fallback to `xcodebuild test` |
| CI | All | GitHub Actions 3 jobs | Cache npm + Gradle |

Tooling choices:
- Keep Jest + ts-jest (`jest.config.js`).
- Android: prefer fakes; use Mockito only where mocking is unavoidable.
- iOS: prefer `swift test` via SPM; use `xcodebuild test` only if needed.

## 7) Migration Plan / Work Items (Backwards Compatible Backlog)
### Phase 0: ~~Compatibility baseline + golden contract~~ (Done)
- Description: document current behavior and define vNext normalized contract + vectors.
- Files/areas: `src/definitions.ts`, `src/predefined-web-responses.ts`, `android/.../Messages.java`, `ios/.../Messages.swift`, `test/`.
- Acceptance criteria:
  - Legacy contract document and vectors exist.
  - vNext normalized contract documented and vectors prepared.
  - Tests cover error codes and `RecordData` shape per platform (legacy mode).
  - No breaking changes to public API.
- Effort: S
- Risk/notes: requires validating existing behavior for null/empty string output.
- Compatibility notes: lock current outputs before refactor to prevent accidental change.

### Phase 1: ~~Refactor for testability (service + adapters)~~ (Done)
- Description: create services and adapters; make bridges thin; add responseFormat config and canonical error codes.
- Files/areas:
  - Web: `src/service/`, `src/adapters/`, `src/core/`, `src/web.ts`, `src/VoiceRecorderImpl.ts` (reduced to adapter).
  - Android: `android/src/main/java/.../VoiceRecorder.java`, new `VoiceRecorderService.java`, new adapters.
  - iOS: `ios/Sources/VoiceRecorder/Bridge/VoiceRecorder.swift`, new `VoiceRecorderService.swift`, new adapters.
- Acceptance criteria:
  - Bridge wrappers contain only parameter parsing and `resolve/reject`.
  - Services are testable without Capacitor imports.
  - Output adapters preserve current external behavior in legacy mode.
  - `plugins.VoiceRecorder.responseFormat` is respected on all platforms.
  - Native uses `call.reject(legacyMessage, canonicalCode)` for all rejects.
- Effort: M
- Risk/notes: avoid changing error message strings or payload shapes; move file IO/duration to background threads.
- Compatibility notes: add tests to detect any behavior drift.

### Phase 2: ~~Web tests expansion~~ (Done)
- Description: add tests for service and contract vectors; mock browser APIs; add normalized-mode tests.
- Files/areas: `test/`, `src/service/`, `src/adapters/`.
- Acceptance criteria:
  - Tests cover permission flow, start/stop/pause/resume, error mapping.
  - Contract tests lock response shapes and error codes in legacy and normalized modes.
- Effort: M
- Risk/notes: jsdom does not implement MediaRecorder; use fakes.
- Compatibility notes: assert legacy output behavior (empty string, reject codes).

### Phase 3: ~~Android unit tests~~ (Done)
- Description: add JVM unit tests for service; optional bridge tests if needed.
- Files/areas: `android/src/test/java/app/independo/capacitorvoicerecorder/`.
- Acceptance criteria:
  - `./gradlew testDebugUnitTest` passes.
  - Service tests cover permission flow, file handling decisions, error mapping.
  - Normalized-mode contract tests pass.
- Effort: M
- Risk/notes: avoid Android framework dependencies in unit tests.
- Compatibility notes: ensure `CANNOT_RECORD_ON_THIS_PHONE` remains the external error string.

### Phase 4: ~~iOS unit tests~~ (Done)
- Description: fix SPM target import and add service/core tests.
- Files/areas: `ios/Tests/VoiceRecorderTests/`, `ios/Sources/VoiceRecorder/Service/VoiceRecorderService.swift`.
- Acceptance criteria:
  - Tests compile for the iOS simulator target.
  - Service tests cover permission flow, stop mapping, error mapping.
  - Normalized-mode contract behavior is covered by mapper tests.
- Effort: M
- Risk/notes: avoid AVFoundation usage in unit tests; use adapters; run via Xcode on an iOS simulator (SwiftPM can't execute iOS bundles on macOS).
- Compatibility notes: keep `FAILED_TO_MERGE_RECORDING` behavior and empty-string output.

### Phase 5: GitHub Actions CI
- Description: add CI jobs for web, Android, iOS.
- Files/areas: `.github/workflows/ci.yml`.
- Acceptance criteria:
  - `test_web`, `test_android`, `test_ios` jobs pass.
  - Caches reduce runtime without changing results.
- Effort: S
- Risk/notes: macOS minutes cost; keep iOS tests lean.
- Compatibility notes: CI gates changes that alter contract tests.

## 8) CI Target State (GitHub Actions)
- `test_web`: Node + npm cache, `npm ci`, `npm test`.
- `test_android`: Java + Gradle cache, `./gradlew testDebugUnitTest` from `android/`.
- `test_ios`: macOS runner, prefer `swift test`; fallback to `xcodebuild test` if needed.
- Caching: use npm cache and Gradle cache; avoid DerivedData cache unless build times justify it.

# 9) Implement an example application
- in the `example/` folder, there is an outdated example application
- update it so that it uses the latest version of capacitor (8) and the latest version of this plugin
- make sure the example app can be used to manually test the plugin on all supported platforms (web, android, ios)
- update the dependabot configuration to also check for updates in the example app
- add a simple test screen that uses the plugin to start, pause, resume and stop a recording and then shows the result (duration, mime type, base64 length, uri if available) and allows to play back the recording
- update the example/README.md to explain how to run the example app and what it does

## 10) Update Documentation
- make sure the documentation of this repo reflects how this plugin is tested
- make sure to update the CONTRIBUTING.md file so that it matches the setup of the repo (this might be out of date not only regarding the tests, but is in need of some more comprehensive explanations and restructuring in general)
- check if we can track code coverage and also report it as a label in the README.md