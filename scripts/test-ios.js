const {spawnSync} = require('node:child_process');

const SCHEME = 'IndependoCapacitorVoiceRecorder';

function parseRuntimeVersion(runtimeId) {
    const match = runtimeId.match(/iOS-(?<version>[0-9-]+)/);
    if (!match?.groups?.version) {
        return null;
    }

    return match.groups.version
        .split('-')
        .map((part) => Number.parseInt(part, 10))
        .filter((part) => Number.isFinite(part));
}

function compareVersionsDesc(a, b) {
    const length = Math.max(a.length, b.length);
    for (let i = 0; i < length; i += 1) {
        const aPart = a[i] ?? 0;
        const bPart = b[i] ?? 0;
        if (aPart !== bPart) {
            return bPart - aPart;
        }
    }

    return 0;
}

function isAvailableDevice(device) {
    if (typeof device.isAvailable === 'boolean') {
        return device.isAvailable;
    }

    if (typeof device.availability === 'string') {
        return device.availability === '(available)';
    }

    return true;
}

function getDevicesByRuntime() {
    const result = spawnSync('xcrun', ['simctl', 'list', 'devices', 'available', '-j'], {encoding: 'utf8'});
    if (result.error || result.status !== 0) {
        throw new Error(
            `Unable to query iOS simulators via xcrun.\n${result.stderr || result.stdout || result.error}`,
        );
    }

    const data = JSON.parse(result.stdout || '{}');
    return data.devices || {};
}

function findDeviceById(devicesByRuntime, deviceId) {
    for (const devices of Object.values(devicesByRuntime)) {
        const match = devices.find((device) => device.udid === deviceId);
        if (match) {
            return match;
        }
    }

    return null;
}

function selectSimulator(devicesByRuntime, env = process.env) {
    if (env.IOS_SIMULATOR_ID) {
        const match = findDeviceById(devicesByRuntime, env.IOS_SIMULATOR_ID);
        if (!match) {
            throw new Error(`Unable to find simulator with id "${env.IOS_SIMULATOR_ID}".`);
        }
        return match;
    }

    const preferredNames = env.IOS_SIMULATOR_NAME
        ? [env.IOS_SIMULATOR_NAME]
        : ['iPhone 15 Pro', 'iPhone 15', 'iPhone 14', 'iPhone 13', 'iPhone 12', 'iPhone 11', 'iPhone SE'];

    const runtimeEntries = Object.entries(devicesByRuntime)
        .map(([runtime, devices]) => ({
            runtime,
            devices,
            version: parseRuntimeVersion(runtime),
        }))
        .filter((entry) => entry.version && entry.devices.length > 0)
        .sort((a, b) => compareVersionsDesc(a.version, b.version));

    for (const entry of runtimeEntries) {
        const availableDevices = entry.devices.filter(isAvailableDevice);
        if (availableDevices.length === 0) {
            continue;
        }

        for (const name of preferredNames) {
            const preferred = availableDevices.find((device) => device.name === name);
            if (preferred) {
                return preferred;
            }
        }

        const iphone = availableDevices.find((device) => device.name?.startsWith('iPhone'));
        if (iphone) {
            return iphone;
        }

        return availableDevices[0];
    }

    throw new Error('No available iOS simulators were found.');
}

function run() {
    const devicesByRuntime = getDevicesByRuntime();
    const device = selectSimulator(devicesByRuntime);

    // eslint-disable-next-line no-console
    console.log(`Running iOS tests on simulator: ${device.name} (${device.udid})`);

    const args = [
        'test',
        '-scheme',
        SCHEME,
        '-destination',
        `platform=iOS Simulator,id=${device.udid}`,
        '-sdk',
        'iphonesimulator',
    ];

    const result = spawnSync('xcodebuild', args, {stdio: 'inherit'});
    process.exit(result.status ?? 1);
}

run();
