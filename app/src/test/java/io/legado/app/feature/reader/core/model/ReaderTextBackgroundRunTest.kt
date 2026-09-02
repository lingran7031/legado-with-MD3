package io.legado.app.feature.reader.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderTextBackgroundRunTest {

    private val image = ReaderTextBackgroundImage("background.png", fit = 3, scale = 1f)
    private val style = ReaderTextStyle(0xFF000000.toInt(), 20f, backgroundImage = image)

    @Test
    fun `merges adjacent text with the same background on one line`() {
        val page = page(
            text(0f, 0f, 10f, 20f, style),
            text(10f, 0f, 25f, 20f, style),
        )

        assertEquals(listOf(ReaderRect(0f, 0f, 25f, 20f)), page.textBackgroundRuns().map { it.bounds })
    }

    @Test
    fun `does not merge across lines gaps or different images`() {
        val other = style.copy(backgroundImage = image.copy(source = "other.png"))
        val page = page(
            text(0f, 0f, 10f, 20f, style),
            text(12f, 0f, 22f, 20f, style),
            text(0f, 20f, 10f, 40f, style),
            text(10f, 20f, 20f, 40f, other),
        )

        assertEquals(4, page.textBackgroundRuns().size)
    }

    @Test
    fun `nine slice run includes the side pieces reserved by pagination`() {
        val framed = image.copy(contentInsetLeftPx = 3f, contentInsetRightPx = 4f)
        val framedStyle = style.copy(backgroundImage = framed)
        val page = page(
            text(3f, 0f, 13f, 20f, framedStyle),
            text(13f, 0f, 23f, 20f, framedStyle),
        )

        assertEquals(ReaderRect(0f, 0f, 27f, 20f), page.textBackgroundRuns().single().bounds)
    }

    @Test
    fun `bitmap width resolves legacy nine slice horizontal margins`() {
        val resolved = image.copy(
            ninePatchLeft = 0.2f,
            ninePatchRight = 0.3f,
            ninePatchTop = 0.1f,
            ninePatchBottom = 0.2f,
        ).withBitmapSize(50, 40)

        assertEquals(10f, resolved.contentInsetLeftPx, 0f)
        assertEquals(15f, resolved.contentInsetRightPx, 0.001f)
        assertEquals(4f, resolved.contentInsetTopPx, 0f)
        assertEquals(8f, resolved.contentInsetBottomPx, 0f)
    }

    private fun text(left: Float, top: Float, right: Float, bottom: Float, textStyle: ReaderTextStyle) =
        ReaderElement.Text(
            bounds = ReaderRect(left, top, right, bottom),
            baselinePx = bottom - 4f,
            value = "字",
            style = textStyle,
            selected = false,
            emphasized = false,
            chapterPosition = 0,
        )

    private fun page(vararg elements: ReaderElement) = ReaderPage(
        id = ReaderPageId(0, 0),
        chapterTitle = "chapter",
        text = "",
        widthPx = 100,
        heightPx = 100,
        elements = elements.toList(),
        contentTopPx = 0f,
        contentBottomPx = 100f,
        revision = 1L,
    )
}
