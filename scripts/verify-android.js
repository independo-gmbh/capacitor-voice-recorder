const {spawnSync} = require('node:child_process');
const fs = require('node:fs');
const path = require('node:path');

function resolveJavaBinary(env = process.env) {
    if (!env.JAVA_HOME) {
        return 'java';
    }

    const javaBinary = path.join(env.JAVA_HOME, 'bin', process.platform === 'win32' ? 'java.exe' : 'java');
    return fs.existsSync(javaBinary) ? javaBinary : 'java';
}

function getJavaMajorVersion(env = process.env) {
    const javaBinary = resolveJavaBinary(env);
    const result = spawnSync(javaBinary, ['-version'], {encoding: 'utf8', env});
    const output = `${result.stdout || ''}\n${result.stderr || ''}`.trim();

    if (result.error) {
        return {ok: false, output, error: result.error};
    }

    const match = output.match(/version "(?<version>[^"]+)"/);
    if (!match?.groups?.version) {
        return {ok: false, output, error: new Error('Unable to parse `java -version` output')};
    }

    const rawVersion = match.groups.version;
    const majorStr = rawVersion.startsWith('1.') ? rawVersion.split('.')[1] : rawVersion.split('.')[0];
    const major = Number.parseInt(majorStr, 10);

    if (!Number.isFinite(major)) {
        return {ok: false, output, error: new Error(`Unable to parse Java major version from "${rawVersion}"`)};
    }

    return {ok: true, output, major};
}

function tryResolveJava21Env() {
    if (process.platform !== 'darwin') {
        return null;
    }

    const result = spawnSync('/usr/libexec/java_home', ['-v', '21'], {encoding: 'utf8'});
    if (result.error || result.status !== 0) {
        return null;
    }

    const javaHome = `${result.stdout || ''}`.trim();
    if (!javaHome) {
        return null;
    }

    const env = {...process.env};
    env.JAVA_HOME = javaHome;
    const javaBin = path.join(javaHome, 'bin');
    env.PATH = `${javaBin}${path.delimiter}${env.PATH || ''}`;
    return env;
}

function run() {
    let envForGradle = process.env;
    let java = getJavaMajorVersion(envForGradle);

    if (!java.ok) {
        const fallbackEnv = tryResolveJava21Env();
        if (fallbackEnv) {
            envForGradle = fallbackEnv;
            java = getJavaMajorVersion(envForGradle);
        }
    }

    if (!java.ok) {
        // eslint-disable-next-line no-console
        console.error('Android verification requires a local Java installation (recommended: JDK 21).');
        // eslint-disable-next-line no-console
        console.error('Failed to run/parse `java -version`.\n');
        // eslint-disable-next-line no-console
        console.error(java.output || String(java.error));
        process.exit(1);
    }

    if (java.major >= 25) {
        const fallbackEnv = tryResolveJava21Env();
        if (fallbackEnv) {
            envForGradle = fallbackEnv;
            java = getJavaMajorVersion(envForGradle);
        }
        if (!java.ok || java.major >= 25) {
            // eslint-disable-next-line no-console
            console.error(`Detected Java ${java.major}. The current Gradle version does not support Java 25+ (\"Unsupported class file major version 69\").`);
            // eslint-disable-next-line no-console
            console.error('Use JDK 21 (recommended) or any supported version up to JDK 24 for `npm run verify:android`.\n');
            // eslint-disable-next-line no-console
            console.error('macOS example:\n  export JAVA_HOME=$(/usr/libexec/java_home -v 21)\n  npm run verify:android');
            process.exit(1);
        }
    }

    const gradle = spawnSync('./gradlew', ['clean', 'build', 'test'], {
        cwd: require('node:path').join(__dirname, '..', 'android'),
        stdio: 'inherit',
        env: envForGradle,
    });

    process.exit(gradle.status ?? 1);
}

run();
