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
    void acceptsHttpAndHttpsDownloadUrls() {
        assertDoesNotThrow(() ->
                DownloadUtil.validateDownloadRequest("https://example.com/file.jar", "0123456789abcdef0123456789abcdef01234567"));
        assertDoesNotThrow(() ->
                DownloadUtil.validateDownloadRequest("http://example.com/file.jar", null));
    }
}
