# Contributing

This guide provides instructions for contributing to this Capacitor plugin.

## Developing

### Local Setup

1. Fork and clone the repo.
2. Install the dependencies.

    ```shell
    npm install
    ```

3. Install SwiftLint if you're on macOS.

    ```shell
    brew install swiftlint
    ```

### Prerequisites for platform testing

- Java 21 (recommended) for Android unit tests and verification.
- Android SDK for `npm run test:android` or `npm run verify:android`.
- Xcode + CocoaPods for `npm run test:ios` or `npm run verify:ios`.

### Scripts

#### `npm run build`

Build the plugin web assets.
It will compile the TypeScript code from `src/` into ESM JavaScript in `dist/esm/`. These files are used in apps with
bundlers when your plugin is imported.

Then, Rollup will bundle the code into a single file at `dist/plugin.js`. This file is used in apps without bundlers by
including it as a script in `index.html`.

#### `npm run docgen`

Generate plugin API documentation using [`@capacitor/docgen`](https://github.com/ionic-team/capacitor-docgen).

The generated documentation will update the `README.md` file.

#### `npm run verify`

Build and validate the web and native projects.

This is useful to run in CI to verify that the plugin builds for all platforms.

#### `npm run lint` / `npm run fmt`

Check formatting and code quality, autoformat/autofix if possible.

This template is integrated with ESLint, Prettier, and SwiftLint. Using these tools is completely optional, but
the [Capacitor Community](https://github.com/capacitor-community/) strives to have consistent code style and structure
for easier cooperation.

#### `npm test`

Run all unit test suites (web, Android, iOS). This requires Android and iOS toolchains. If you only have one platform
available, run the platform-specific test command instead.

#### `npm run test:web`

Run Jest unit tests for the web implementation.

#### `npm run test:web:coverage`

Generate local coverage for the web tests (coverage is not uploaded in CI).

#### `npm run test:android`

Run Android JVM unit tests (`testDebugUnitTest`). Requires Java 21 and a local Android SDK.

#### `npm run test:android:coverage`

Run Android unit tests plus JaCoCo coverage XML generation.

#### `npm run test:ios`

Run iOS XCTest via `xcodebuild test`. The script auto-selects a simulator; override with `IOS_SIMULATOR_ID` or
`IOS_SIMULATOR_NAME` if needed.

#### `npm run test:ios:coverage`

Run iOS XCTest with coverage enabled and generate a Cobertura XML report.

#### Example App

The example app in `example/` is used for manual validation across platforms. See [./example/README.md](./example/README.md) 
for setup and run instructions.

## Publishing

There is a `prepublishOnly` hook in `package.json` which prepares the plugin before publishing, so all you need to do is
run:

```shell
npm publish
```

> **Note**: The [`files`](https://docs.npmjs.com/cli/v7/configuring-npm/package-json#files) array in `package.json`
> specifies which files get published. If you rename files/directories or add files elsewhere, you may need to update
> it.
