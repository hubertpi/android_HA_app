package io.homeassistant.companion.android.launch

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ForkUpdatePromptTest {
    @Test
    fun `Given a newer fork release with an APK when checking then return its release page`() {
        val body = """{"tag_name":"apk-3","assets":[{"name":"Home-Assistant-Fork.apk"}]}"""

        assertEquals("https://github.com/hubertpi/android_HA_app/releases/tag/apk-3", parseForkUpdate(body, 2))
    }

    @Test
    fun `Given an installed release when checking then skip the prompt`() {
        val body = """{"tag_name":"apk-3","assets":[{"name":"Home-Assistant-Fork.apk"}]}"""

        assertNull(parseForkUpdate(body, 3))
    }

    @Test
    fun `Given a release without the fork APK when checking then skip the prompt`() {
        val body = """{"tag_name":"apk-3","assets":[{"name":"Source.zip"}]}"""

        assertNull(parseForkUpdate(body, 2))
    }

    @Test
    fun `Given a prerelease when checking then skip the prompt`() {
        val body = """{"tag_name":"apk-3","prerelease":true,"assets":[{"name":"Home-Assistant-Fork.apk"}]}"""

        assertNull(parseForkUpdate(body, 2))
    }
}
