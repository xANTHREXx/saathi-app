package app.saathi.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VideoLinkTest {
    @Test
    fun returnsNullForBlankOrNonHttpsUrl() {
        assertNull(videoLink(""))
        assertNull(videoLink("   "))
        assertNull(videoLink("http://youtu.be/abc"))
        assertNull(videoLink("ftp://example.com"))
        assertNull(videoLink("javascript:alert(1)"))
    }

    @Test
    fun returnsHttpsUrlTrimmed() {
        assertEquals("https://youtu.be/abc", videoLink("https://youtu.be/abc"))
        assertEquals("https://youtu.be/abc", videoLink("  https://youtu.be/abc  "))
    }
}
