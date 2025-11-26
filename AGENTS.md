# Repository Guidelines

## Project Structure & Module Organization
- Source plugin code lives in `src/` (TypeScript bridge plus web implementation); shared helpers are under `src/helper/`.
- Native implementations reside in `android/` (Gradle module) and `ios/Plugin/` (Swift). The CocoaPods spec is `IndependoCapacitorVoiceRecorder.podspec`.
- Tests sit in `test/` (Jest + ts-jest). Build output is generated into `dist/` and should not be committed manually.
- The example app for manual validation is in `example/`.

## Build, Test, and Development Commands
- `npm run build` — cleans, regenerates docs (`docgen`), runs `tsc`, then bundles via Rollup.
- `npm test` — runs Jest tests in `test/`.
- `npm run lint` / `npm run fmt` — check or auto-fix TypeScript lint issues using the Ionic ESLint preset.
- `npm run swiftlint` — lint Swift sources (requires SwiftLint installed).
- `npm run verify` — end-to-end check: iOS build (`xcodebuild`), Android Gradle build/tests, then web build. Requires Xcode + Android SDK/NDK installed.
- `npm run watch` — incremental TypeScript compilation during development.

## Coding Style & Naming Conventions
- Follow the Ionic ESLint + Prettier configs; default spacing is 4 spaces, single quotes preferred.
- TypeScript: classes/interfaces in `PascalCase`, functions/variables in `camelCase`, and error constants remain `camelCase`.
- File naming follows existing patterns (`VoiceRecorderImpl.ts`, helper utilities in descriptive kebab-case where already used).
- Avoid editing `dist/` directly; rely on `npm run build`.

## Testing Guidelines
- Place specs under `test/` with the `.test.ts` suffix (e.g., `VoiceRecorderImpl.test.ts`).
- Use Jest with ts-jest; mock browser APIs such as `MediaRecorder` and `navigator.mediaDevices` to keep tests deterministic.
- Prefer asserting high-level behavior (permission checks, state transitions, error paths) rather than implementation details.
- Run `npm test` before submitting; for native changes, also execute platform builds via `npm run verify:ios` / `npm run verify:android` when available.

## Commit & Pull Request Guidelines
- Use Conventional Commits (`feat:`, `fix:`, `chore:`, `docs:`, `test:`, `refactor:`); this repo relies on semantic-release tooling.
- Keep commits scoped and descriptive (e.g., `fix: handle missing microphone permission on web`).
- PRs should include: summary of changes, affected platforms (web/android/ios), test evidence (`npm test`, `npm run verify` excerpts if run), and notes on doc updates (`npm run docgen` when API comments change).
- Do not commit generated artifacts (`dist/`, build outputs); ensure lockfile changes are intentional.

## Platform Notes & Safety
- iOS builds require CocoaPods (`pod install`) and a recent Xcode; Android builds require a configured JDK/SDK and Gradle wrapper.
- Add required microphone permissions in consuming apps (`AndroidManifest.xml`, `Info.plist`) as documented in `README.md`.
