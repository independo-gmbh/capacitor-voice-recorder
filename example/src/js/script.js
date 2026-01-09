import { VoiceRecorder } from '@independo/capacitor-voice-recorder';

const state = {
    lastRecording: null,
};

const elements = {
    capabilityStatus: document.querySelector('#record-capability-status'),
    permissionStatus: document.querySelector('#permission-status'),
    recordingStatus: document.querySelector('#recording-status'),
    duration: document.querySelector('#recording-duration'),
    mimeType: document.querySelector('#recording-mime-type'),
    base64Length: document.querySelector('#recording-base64-length'),
    uri: document.querySelector('#recording-uri'),
    errorOutput: document.querySelector('#error-output'),
    playback: document.querySelector('#playback-audio'),
    checkCapability: document.querySelector('#check-capability'),
    checkPermission: document.querySelector('#check-permission'),
    requestPermission: document.querySelector('#request-permission'),
    startRecording: document.querySelector('#start-recording'),
    pauseRecording: document.querySelector('#pause-recording'),
    resumeRecording: document.querySelector('#resume-recording'),
    stopRecording: document.querySelector('#stop-recording'),
    clearRecording: document.querySelector('#clear-recording'),
};

function setText(element, value) {
    if (element) {
        element.textContent = value;
    }
}

function normalizeError(error) {
    if (!error) {
        return 'Unknown error';
    }
    if (typeof error === 'string') {
        return error;
    }
    if (typeof error === 'object') {
        const code = error.code ? String(error.code) : null;
        const message = error.message ? String(error.message) : null;
        if (code && message) {
            return `${code}: ${message}`;
        }
        if (code) {
            return code;
        }
        if (message) {
            return message;
        }
    }
    try {
        return JSON.stringify(error);
    } catch (stringifyError) {
        return String(error);
    }
}

function setError(error) {
    setText(elements.errorOutput, normalizeError(error));
}

function clearError() {
    setText(elements.errorOutput, 'None');
}

function updateRecordingDetails() {
    const recording = state.lastRecording;
    if (!recording) {
        setText(elements.duration, '—');
        setText(elements.mimeType, '—');
        setText(elements.base64Length, '—');
        setText(elements.uri, '—');
        if (elements.playback) {
            elements.playback.removeAttribute('src');
            elements.playback.load();
        }
        return;
    }

    const base64 = recording.recordDataBase64 ?? '';
    const uri = recording.uri ?? '';
    const mimeType = recording.mimeType ?? 'audio/webm';

    setText(elements.duration, recording.msDuration != null ? `${recording.msDuration} ms` : '—');
    setText(elements.mimeType, mimeType || '—');
    setText(elements.base64Length, base64 ? `${base64.length} chars` : '0');
    setText(elements.uri, uri || '—');

    if (elements.playback) {
        if (uri) {
            elements.playback.src = uri;
        } else if (base64) {
            elements.playback.src = `data:${mimeType};base64,${base64}`;
        } else {
            elements.playback.removeAttribute('src');
        }
        elements.playback.load();
    }
}

async function refreshCapability() {
    try {
        const result = await VoiceRecorder.canDeviceVoiceRecord();
        setText(elements.capabilityStatus, result.value ? 'Can' : 'Cannot');
    } catch (error) {
        setText(elements.capabilityStatus, 'Unknown');
        setError(error);
    }
}

async function refreshPermission() {
    try {
        const result = await VoiceRecorder.hasAudioRecordingPermission();
        setText(elements.permissionStatus, result.value ? 'Granted' : 'Not granted');
    } catch (error) {
        setText(elements.permissionStatus, 'Unknown');
        setError(error);
    }
}

async function refreshStatus() {
    try {
        const result = await VoiceRecorder.getCurrentStatus();
        setText(elements.recordingStatus, result.status);
    } catch (error) {
        setText(elements.recordingStatus, 'Unknown');
        setError(error);
    }
}

async function handleRequestPermission() {
    clearError();
    try {
        await VoiceRecorder.requestAudioRecordingPermission();
    } catch (error) {
        setError(error);
    }
    await refreshPermission();
}

async function handleStart() {
    clearError();
    try {
        const result = await VoiceRecorder.startRecording();
        if (!result.value) {
            setError('Start returned false.');
        }
    } catch (error) {
        setError(error);
    }
    await refreshStatus();
}

async function handlePause() {
    clearError();
    try {
        const result = await VoiceRecorder.pauseRecording();
        if (!result.value) {
            setError('Recording already paused.');
        }
    } catch (error) {
        setError(error);
    }
    await refreshStatus();
}

async function handleResume() {
    clearError();
    try {
        const result = await VoiceRecorder.resumeRecording();
        if (!result.value) {
            setError('Recording already running.');
        }
    } catch (error) {
        setError(error);
    }
    await refreshStatus();
}

async function handleStop() {
    clearError();
    try {
        const result = await VoiceRecorder.stopRecording();
        state.lastRecording = result.value ?? null;
        updateRecordingDetails();
    } catch (error) {
        setError(error);
    }
    await refreshStatus();
}

function handleClear() {
    state.lastRecording = null;
    updateRecordingDetails();
}

elements.checkCapability?.addEventListener('click', () => refreshCapability());
elements.checkPermission?.addEventListener('click', () => refreshPermission());
elements.requestPermission?.addEventListener('click', () => handleRequestPermission());
elements.startRecording?.addEventListener('click', () => handleStart());
elements.pauseRecording?.addEventListener('click', () => handlePause());
elements.resumeRecording?.addEventListener('click', () => handleResume());
elements.stopRecording?.addEventListener('click', () => handleStop());
elements.clearRecording?.addEventListener('click', () => handleClear());

clearError();
updateRecordingDetails();
refreshCapability();
refreshPermission();
refreshStatus();
