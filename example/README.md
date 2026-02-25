## Voice Recorder Example App

Use this app to manually validate the Capacitor Voice Recorder plugin on web, Android, and iOS.

### Prerequisites

- Node.js + pnpm
- Android Studio (for Android)
- Xcode + CocoaPods (for iOS)

### Setup

From the repo root:

```bash
cd example
pnpm install
pnpm build
pnpm exec cap sync
```

The example links the local plugin via the pnpm workspace (`workspace:*`), so no extra publish step is needed. Android and iOS
projects are already committed, so you do not need `npx cap add`.

### Run on Web

```bash
pnpm start
```

### Run on Android

```bash
pnpm exec cap open android
```

Or run directly from the CLI:

```bash
pnpm exec cap run android
```

### Run on iOS

```bash
pnpm exec cap open ios
```

Or run directly from the CLI:

```bash
pnpm exec cap run ios
```

### Test Checklist

- Check device capability + permission status.
- Request permission and confirm the status updates.
- Start, pause, resume, and stop a recording.
- Verify the metadata (duration, mime type, base64 length, URI) and playback behavior.

### After Plugin Changes

If you update the plugin source, rebuild and resync before launching native targets:

```bash
pnpm -C .. build
pnpm exec cap sync
```
