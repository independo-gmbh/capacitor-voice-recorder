const {spawnSync} = require('node:child_process');

function getJavaMajorVersion() {
    const result = spawnSync('java', ['-version'], {encoding: 'utf8'});
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

function run() {
    const java = getJavaMajorVersion();

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
        // eslint-disable-next-line no-console
        console.error(`Detected Java ${java.major}. The current Gradle version does not support Java 25+ (\"Unsupported class file major version 69\").`);
        // eslint-disable-next-line no-console
        console.error('Use JDK 21 (recommended) or any supported version up to JDK 24 for `npm run verify:android`.\n');
        // eslint-disable-next-line no-console
        console.error('macOS example:\n  export JAVA_HOME=$(/usr/libexec/java_home -v 21)\n  npm run verify:android');
        process.exit(1);
    }

    const gradle = spawnSync('./gradlew', ['clean', 'build', 'test'], {
        cwd: require('node:path').join(__dirname, '..', 'android'),
        stdio: 'inherit',
    });

    process.exit(gradle.status ?? 1);
}

run();
