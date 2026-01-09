const {spawnSync} = require('node:child_process');
const fs = require('node:fs');
const path = require('node:path');

function parseArgs(argv = process.argv) {
    const options = {
        xcresultPath: null,
        outputPath: null,
    };

    for (let i = 2; i < argv.length; i += 1) {
        const arg = argv[i];
        if (arg === '--xcresult') {
            options.xcresultPath = argv[i + 1];
            i += 1;
        } else if (arg === '--output') {
            options.outputPath = argv[i + 1];
            i += 1;
        }
    }

    return options;
}

function runXccovArchive(xcresultPath) {
    const result = spawnSync('xcrun', ['xccov', 'view', '--archive', '--json', xcresultPath], {encoding: 'utf8'});
    if (result.error || result.status !== 0) {
        throw new Error(
            `Failed to read xccov archive JSON.\n${result.stderr || result.stdout || result.error}`,
        );
    }

    try {
        return JSON.parse(result.stdout || '{}');
    } catch (error) {
        throw new Error(`Failed to parse xccov JSON: ${error.message}`);
    }
}

function runXccovReport(xcresultPath) {
    const result = spawnSync('xcrun', ['xccov', 'view', '--report', '--json', xcresultPath], {encoding: 'utf8'});
    if (result.error || result.status !== 0) {
        throw new Error(
            `Failed to read xccov report JSON.\n${result.stderr || result.stdout || result.error}`,
        );
    }

    try {
        return JSON.parse(result.stdout || '{}');
    } catch (error) {
        throw new Error(`Failed to parse xccov report JSON: ${error.message}`);
    }
}

function runXccovArchiveFile(xcresultPath, filePath) {
    const result = spawnSync(
        'xcrun',
        ['xccov', 'view', '--archive', '--file', filePath, '--json', xcresultPath],
        {encoding: 'utf8'},
    );
    if (result.error || result.status !== 0) {
        throw new Error(
            `Failed to read xccov archive for ${filePath}.\n${result.stderr || result.stdout || result.error}`,
        );
    }

    try {
        return JSON.parse(result.stdout || '{}');
    } catch (error) {
        throw new Error(`Failed to parse xccov archive JSON: ${error.message}`);
    }
}

function collectFiles(node, byPath) {
    if (!node) {
        return;
    }
    if (Array.isArray(node)) {
        node.forEach((child) => collectFiles(child, byPath));
        return;
    }
    if (typeof node !== 'object') {
        return;
    }

    if (Array.isArray(node.targets)) {
        node.targets.forEach((target) => collectFiles(target, byPath));
    }

    if (Array.isArray(node.files)) {
        node.files.forEach((file) => collectFiles(file, byPath));
    }

    if (typeof node.path === 'string' && (Array.isArray(node.lines) || Array.isArray(node.lineExecutionCounts) || Array.isArray(node.executableLines))) {
        byPath.set(node.path, node);
    }
}

function collectFilePaths(node, paths) {
    if (!node) {
        return;
    }
    if (Array.isArray(node)) {
        node.forEach((child) => collectFilePaths(child, paths));
        return;
    }
    if (typeof node !== 'object') {
        return;
    }

    if (Array.isArray(node.targets)) {
        node.targets.forEach((target) => collectFilePaths(target, paths));
    }

    if (Array.isArray(node.files)) {
        node.files.forEach((file) => collectFilePaths(file, paths));
    }

    if (typeof node.path === 'string') {
        paths.add(node.path);
    }
}

function toNumber(value) {
    const numberValue = Number(value);
    return Number.isFinite(numberValue) ? numberValue : null;
}

function getLineEntries(file) {
    const entries = new Map();

    if (Array.isArray(file.lines)) {
        file.lines.forEach((line) => {
            const number = toNumber(line.lineNumber ?? line.line ?? line.number);
            const hits = toNumber(
                line.executionCount ?? line.executionCounts ?? line.count ?? line.hitCount ?? line.executions,
            );
            if (number != null && hits != null) {
                entries.set(number, hits);
            }
        });
    } else if (Array.isArray(file.lineExecutionCounts)) {
        file.lineExecutionCounts.forEach((hits, index) => {
            const count = toNumber(hits);
            if (count != null) {
                entries.set(index + 1, count);
            }
        });
    } else if (Array.isArray(file.executableLines)) {
        const covered = new Set(Array.isArray(file.coveredLines) ? file.coveredLines : []);
        file.executableLines.forEach((lineNumber) => {
            const number = toNumber(lineNumber);
            if (number != null) {
                entries.set(number, covered.has(lineNumber) ? 1 : 0);
            }
        });
    }

    return Array.from(entries.entries())
        .map(([number, hits]) => ({number, hits}))
        .filter((entry) => entry.number > 0)
        .sort((a, b) => a.number - b.number);
}

function escapeXml(value) {
    return String(value)
        .replace(/&/g, '&amp;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&apos;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;');
}

function formatRate(numerator, denominator) {
    if (!denominator) {
        return '0';
    }
    return (numerator / denominator).toFixed(4);
}

function toRelativeFilePath(filePath, rootDir) {
    const relative = path.relative(rootDir, filePath);
    return relative.startsWith('..') ? filePath : relative.split(path.sep).join('/');
}

function buildCobertura(files, rootDir) {
    const classes = [];
    let totalLines = 0;
    let totalCovered = 0;

    files.forEach((file) => {
        const lines = getLineEntries(file);
        if (!lines.length) {
            return;
        }

        const covered = lines.filter((line) => line.hits > 0).length;
        const valid = lines.length;
        totalLines += valid;
        totalCovered += covered;

        const filename = toRelativeFilePath(file.path, rootDir);
        const className = filename.replace(/\.[^/.]+$/, '').split('/').join('.');
        const lineRate = formatRate(covered, valid);
        const lineElements = lines
            .map((line) => `          <line number="${line.number}" hits="${line.hits}" branch="false" />`)
            .join('\n');

        classes.push(
            [
                `      <class name="${escapeXml(className)}" filename="${escapeXml(filename)}" line-rate="${lineRate}" branch-rate="0" complexity="0">`,
                '        <lines>',
                lineElements,
                '        </lines>',
                '      </class>',
            ].join('\n'),
        );
    });

    const timestamp = Math.floor(Date.now() / 1000);
    const overallRate = formatRate(totalCovered, totalLines);

    return [
        '<?xml version="1.0" ?>',
        '<!DOCTYPE coverage SYSTEM "http://cobertura.sourceforge.net/xml/coverage-04.dtd">',
        `<coverage line-rate="${overallRate}" branch-rate="0" lines-covered="${totalCovered}" lines-valid="${totalLines}" branches-covered="0" branches-valid="0" complexity="0" timestamp="${timestamp}">`,
        '  <sources>',
        `    <source>${escapeXml(rootDir)}</source>`,
        '  </sources>',
        '  <packages>',
        `    <package name="." line-rate="${overallRate}" branch-rate="0" complexity="0">`,
        '      <classes>',
        classes.join('\n'),
        '      </classes>',
        '    </package>',
        '  </packages>',
        '</coverage>',
        '',
    ].join('\n');
}

function run() {
    const options = parseArgs();
    if (!options.xcresultPath || !options.outputPath) {
        // eslint-disable-next-line no-console
        console.error('Usage: node scripts/xccov-to-cobertura.js --xcresult <path> --output <path>');
        process.exit(1);
    }

    const rootDir = process.cwd();
    const archiveData = runXccovArchive(options.xcresultPath);
    const filesByPath = new Map();
    collectFiles(archiveData, filesByPath);

    let files = Array.from(filesByPath.values());
    let coberturaXml = buildCobertura(
        files.sort((a, b) => String(a.path).localeCompare(String(b.path))),
        rootDir,
    );

    if (!files.length || coberturaXml.includes('lines-valid="0"')) {
        const reportData = runXccovReport(options.xcresultPath);
        const reportPathsSet = new Set();
        collectFilePaths(reportData, reportPathsSet);
        const reportPaths = Array.from(reportPathsSet.values());
        const fallbackFiles = [];

        reportPaths.forEach((filePath) => {
            const archiveFile = runXccovArchiveFile(options.xcresultPath, filePath);
            if (archiveFile && !archiveFile.path) {
                archiveFile.path = filePath;
            }
            fallbackFiles.push(archiveFile);
        });

        if (fallbackFiles.length) {
            files = fallbackFiles;
            coberturaXml = buildCobertura(
                files.sort((a, b) => String(a.path).localeCompare(String(b.path))),
                rootDir,
            );
        }
    }

    if (!files.length) {
        // eslint-disable-next-line no-console
        console.error('No coverage files found in xccov output.');
        process.exit(1);
    }

    fs.mkdirSync(path.dirname(options.outputPath), {recursive: true});
    fs.writeFileSync(options.outputPath, coberturaXml, 'utf8');
}

run();
