package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.MatchResult
import com.example.util.TimeFormatter
import com.example.util.TitleMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        val expectedName = if (BuildConfig.APP_ROLE == "ADMIN") "Kingo Admin" else "Kingo King"
        assertEquals(expectedName, appName)
    }

    @Test
    fun `test time formatting`() {
        assertEquals("03:00", TimeFormatter.formatSecondsToMmSs(180))
        assertEquals("00:20", TimeFormatter.formatSecondsToMmSs(20))
        assertEquals("01:15", TimeFormatter.formatSecondsToMmSs(75))
    }

    @Test
    fun `test clean youtube url removes timestamp and chapters`() {
        val dirtyUrl1 = "https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=19s"
        val cleaned1 = TitleMatcher.cleanYouTubeUrl(dirtyUrl1)
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", cleaned1)

        val dirtyUrl2 = "https://youtu.be/dQw4w9WgXcQ?t=19"
        val cleaned2 = TitleMatcher.cleanYouTubeUrl(dirtyUrl2)
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", cleaned2)
    }

    @Test
    fun `test title normalization and matching`() {
        val normalized = TitleMatcher.normalize("Rick Astley - Never Gonna Give You Up (Official Music Video)!!")
        assertEquals("rick astley never gonna give you up official music video", normalized)

        val match = TitleMatcher.evaluateMatch(
            playingTitle = "Rick Astley - Never Gonna Give You Up (Official Music Video)",
            taskTitle = "Never Gonna Give You Up",
            playingArtist = "Rick Astley",
            taskAuthor = "RickAstleyVEVO"
        )
        assertEquals(MatchResult.MATCH, match)

        val mismatch = TitleMatcher.evaluateMatch(
            playingTitle = "Some Totally Different Video Song",
            taskTitle = "Never Gonna Give You Up",
            playingArtist = "Other Artist",
            taskAuthor = "RickAstleyVEVO"
        )
        assertEquals(MatchResult.MISMATCH, mismatch)
    }

    @Test
    fun `test video id extraction`() {
        val id1 = TitleMatcher.extractVideoId("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
        assertEquals("dQw4w9WgXcQ", id1)

        val id2 = TitleMatcher.extractVideoId("https://youtu.be/dQw4w9WgXcQ")
        assertEquals("dQw4w9WgXcQ", id2)

        val thumb = TitleMatcher.getThumbnailUrl("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
        assertNotNull(thumb)
    }
}
