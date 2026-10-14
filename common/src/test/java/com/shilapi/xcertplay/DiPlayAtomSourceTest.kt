package com.shilapi.xcertplay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class DiPlayAtomSourceTest {
    @Test fun skipsPrereleaseAndFindsStableRelease() {
        val input = """<?xml version="1.0"?>
            <feed xmlns="http://www.w3.org/2005/Atom">
              <entry>
                <id>tag:github.com,2008:Repository/123/v0.2.48-t32.1-beta</id>
                <link rel="alternate" href="https://github.com/winkinwong/DiPlay-Legacy-Android-T32/releases/tag/v0.2.48-t32.1-beta"/>
                <title>Preview</title>
              </entry>
              <entry>
                <id>tag:github.com,2008:Repository/123/v0.2.47-t32.2</id>
                <link rel="alternate" href="https://github.com/winkinwong/DiPlay-Legacy-Android-T32/releases/tag/v0.2.47-t32.2"/>
                <title>Stable release</title>
                <content type="html">&lt;p&gt;Fixes legacy USB&lt;/p&gt;</content>
              </entry>
            </feed>"""
        val release = DiPlayAtomSource.newest(input, "0.2.46-t32.1")
        assertEquals("v0.2.47-t32.2", release?.tag)
        assertEquals("Fixes legacy USB", release?.notes)
        assertNull(DiPlayAtomSource.newest(input, "0.2.47-t32.2"))
    }

    @Test fun ignoresEntriesWhoseUrlIsNotOurRelease() {
        // Upstream releases must never be offered, even when they look newer.
        val xml = """<feed><entry>
            <id>tag:github.com,2008:Repository/123/v0.2.56</id>
            <link rel="alternate" href="https://github.com/programmerguohuajing/DiPlay-Legacy-Android/releases/tag/v0.2.56"/>
            <title>Upstream</title>
            </entry></feed>"""
        assertNull(DiPlayAtomSource.newest(xml, "0.2.46-t32.1"))
        val other = """<feed><entry>
            <id>tag:github.com,2008:Repository/123/v0.2.40</id>
            <link rel="alternate" href="https://example.org/other/v0.2.40"/>
            <title>Untrusted</title>
            </entry></feed>"""
        assertNull(DiPlayAtomSource.newest(other, "0.2.33"))
    }
}
