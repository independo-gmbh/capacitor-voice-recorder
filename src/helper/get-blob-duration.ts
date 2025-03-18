/**
 * @param {Blob | string} blob
 * @returns {Promise<number>} Blob duration in seconds.
 */
export default function getBlobDuration(blob: Blob | string): Promise<number> {
    const tempVideoEl = document.createElement('video');
    if (!tempVideoEl) throw new Error('Failed to create video element');
    const durationP = new Promise<number>((resolve, reject) => {
        tempVideoEl.addEventListener('loadedmetadata', () => {
            // Chrome bug: https://bugs.chromium.org/p/chromium/issues/detail?id=642012
            if (tempVideoEl.duration === Infinity) {
                tempVideoEl.currentTime = Number.MAX_SAFE_INTEGER;
                tempVideoEl.ontimeupdate = () => {
                    tempVideoEl.ontimeupdate = null;
                    resolve(tempVideoEl.duration);
                    tempVideoEl.currentTime = 0;
                };
            } else {
                resolve(tempVideoEl.duration);
            }
        });

        tempVideoEl.onerror = (event) => {
            const error = (event as ErrorEvent).error || new Error('Unknown error occurred');
            reject(error);
        };
    });

    tempVideoEl.src = typeof blob === 'string' ? blob : URL.createObjectURL(blob);

    return durationP;
}
