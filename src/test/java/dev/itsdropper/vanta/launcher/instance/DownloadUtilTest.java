package dev.itsdropper.vanta.launcher.instance;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class DownloadUtilTest {

    @Test
    void acceptsExactSegmentByteRange() throws Exception {
        assertDoesNotThrow(() ->
                DownloadUtil.validateContentRange("bytes 0-99/400", 0, 99, 400));
    }

    @Test
    void acceptsResumeRangeStartingAtExistingFileLength() throws Exception {
        assertDoesNotThrow(() ->
                DownloadUtil.validateContentRange("bytes 150-399/400", 150, -1, -1));
    }

    @Test
    void rejectsWrongStartOffset() {
        assertThrows(IOException.class, () ->
                DownloadUtil.validateContentRange("bytes 149-399/400", 150, -1, -1));
    }

    @Test
    void rejectsWrongSegmentEnd() {
        assertThrows(IOException.class, () ->
                DownloadUtil.validateContentRange("bytes 0-98/400", 0, 99, 400));
    }

    @Test
    void rejectsWrongTotalLength() {
        assertThrows(IOException.class, () ->
                DownloadUtil.validateContentRange("bytes 0-99/401", 0, 99, 400));
    }

    @Test
    void rejectsMissingOrMalformedContentRange() {
        assertThrows(IOException.class, () ->
                DownloadUtil.validateContentRange(null, 0, 99, 400));
        assertThrows(IOException.class, () ->
                DownloadUtil.validateContentRange("bytes */400", 0, 99, 400));
    }

    @Test
    void rejectsInvalidDownloadUrlsAndDigests() {
        assertThrows(IllegalArgumentException.class, () ->
                DownloadUtil.validateDownloadRequest("file:///etc/passwd", null));
        assertThrows(IllegalArgumentException.class, () ->
                DownloadUtil.validateDownloadRequest("https://example.com/file.jar", "not-a-sha1"));
    }

    @Test
    void retriesOnlyTransientHttpStatuses() {
        assertTrue(DownloadUtil.isRetryableHttpStatus(408));
        assertTrue(DownloadUtil.isRetryableHttpStatus(425));
        assertTrue(DownloadUtil.isRetryableHttpStatus(429));
        assertTrue(DownloadUtil.isRetryableHttpStatus(500));
        assertTrue(DownloadUtil.isRetryableHttpStatus(503));
        assertFalse(DownloadUtil.isRetryableHttpStatus(400));
        assertFalse(DownloadUtil.isRetryableHttpStatus(401));
        assertFalse(DownloadUtil.isRetryableHttpStatus(403));
        assertFalse(DownloadUtil.isRetryableHttpStatus(404));
        assertFalse(DownloadUtil.isRetryableHttpStatus(501));
    }

    @Test
    void retryBackoffGrowsAndRemainsBounded() {
        IOException transientFailure = new IOException("temporary network failure");
        long first = DownloadUtil.retryDelayMillis(1, transientFailure);
        long third = DownloadUtil.retryDelayMillis(3, transientFailure);
        long finalAttempt = DownloadUtil.retryDelayMillis(20, transientFailure);
        assertTrue(first >= 1_000L && first <= 1_250L);
        assertTrue(third >= 4_000L && third <= 4_250L);
        assertTrue(finalAttempt >= 30_000L && finalAttempt <= 30_250L);
    }

    @Test
    void rejectsMissingDownloadUrl() {
        assertThrows(IllegalArgumentException.class, () ->
                DownloadUtil.validateDownloadRequest(null, null));
        assertThrows(IllegalArgumentException.class, () ->
                DownloadUtil.validateDownloadRequest("   ", null));
    }

    @Test
    void acceptsHttpAndHttpsDownloadUrls() {
        assertDoesNotThrow(() ->
                DownloadUtil.validateDownloadRequest("https://example.com/file.jar", "0123456789abcdef0123456789abcdef01234567"));
        assertDoesNotThrow(() ->
                DownloadUtil.validateDownloadRequest("http://example.com/file.jar", null));
    }
}
