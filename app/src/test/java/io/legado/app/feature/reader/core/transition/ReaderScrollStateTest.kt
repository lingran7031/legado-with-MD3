package io.legado.app.feature.reader.core.transition

import io.legado.app.feature.reader.core.model.ReaderPageId
import io.legado.app.feature.reader.core.model.ReaderElement
import io.legado.app.feature.reader.core.model.ReaderPage
import io.legado.app.feature.reader.core.model.ReaderRect
import io.legado.app.feature.reader.core.model.ReaderTextStyle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderScrollStateTest {
    private val style = ReaderTextStyle(0, 16f)

    @Test fun pageCrossingFreezesOldWindowUntilTargetPageArrives() {
        assertTrue(ReaderScrollPolicy.canApplyDelta(pendingPageTarget = null))
        assertFalse(ReaderScrollPolicy.canApplyDelta(ReaderPageId(0, 1)))
    }

    @Test fun crossingNextCarriesRemainder() {
        val result = ReaderScrollPolicy.apply(-900f, -250f, 1000f, 1000f, 800f, true, true)
        assertEquals(ReaderScrollCrossing.NEXT, result.crossing)
        assertEquals(-150f, result.offsetPx)
    }

    @Test fun crossingPreviousUsesPreviousExtent() {
        val result = ReaderScrollPolicy.apply(-20f, 80f, 900f, 1000f, 800f, true, true)
        assertEquals(ReaderScrollCrossing.PREVIOUS, result.crossing)
        assertEquals(-840f, result.offsetPx)
    }

    @Test fun finalPageClampsBottomToViewport() {
        val result = ReaderScrollPolicy.apply(-300f, -900f, 1000f, 1200f, 800f, true, false)
        assertTrue(result.hitBoundary)
        assertEquals(-400f, result.offsetPx)
    }

    @Test fun pageChangeKeepsOnlyTheCrossingRemainderForTheExpectedTarget() {
        val target = ReaderPageId(2, 4)
        assertEquals(-35f, ReaderScrollPolicy.offsetAfterPageChange(-35f, target, target), 0f)
        assertEquals(0f, ReaderScrollPolicy.offsetAfterPageChange(-35f, target, ReaderPageId(2, 8)), 0f)
        assertEquals(0f, ReaderScrollPolicy.offsetAfterPageChange(-35f, null, target), 0f)
    }

    @Test fun textPageStepsKeepOneVisibleRowInBothDirections() {
        val page = scrollPage(
            listOf(
                text(0f, 20f, 0),
                text(20f, 40f, 1),
                text(40f, 60f, 2),
                text(60f, 80f, 3),
            ),
        )

        assertEquals(-60f, ReaderScrollPolicy.pageStep(page, 0f, ReaderTurnDirection.NEXT), 0f)
        assertEquals(60f, ReaderScrollPolicy.pageStep(page, 0f, ReaderTurnDirection.PREVIOUS), 0f)
    }

    @Test fun visibleRowsAreCalculatedAfterTheCurrentScrollOffset() {
        val page = scrollPage(
            listOf(
                text(0f, 20f, 0),
                text(20f, 40f, 1),
                text(80f, 100f, 2),
                text(140f, 160f, 3),
            ),
        )

        assertEquals(-40f, ReaderScrollPolicy.pageStep(page, -40f, ReaderTurnDirection.NEXT), 0f)
        assertEquals(20f, ReaderScrollPolicy.pageStep(page, -40f, ReaderTurnDirection.PREVIOUS), 0f)
    }

    @Test fun nonInlineImageAndEmptyPagesUseAFullViewportStep() {
        val image = ReaderElement.Image(ReaderRect(0f, 10f, 80f, 70f), "image", null)
        val imagePage = scrollPage(listOf(image), inlineImages = false)
        val emptyPage = scrollPage(emptyList())

        assertEquals(-80f, ReaderScrollPolicy.pageStep(imagePage, 0f, ReaderTurnDirection.NEXT), 0f)
        assertEquals(80f, ReaderScrollPolicy.pageStep(imagePage, 0f, ReaderTurnDirection.PREVIOUS), 0f)
        assertEquals(-80f, ReaderScrollPolicy.pageStep(emptyPage, 0f, ReaderTurnDirection.NEXT), 0f)
    }

    private fun scrollPage(
        elements: List<ReaderElement>,
        inlineImages: Boolean = true,
    ) = ReaderPage(
        id = ReaderPageId(0, 0),
        chapterTitle = "",
        text = "",
        widthPx = 100,
        heightPx = 100,
        contentTopPx = 10f,
        contentBottomPx = 90f,
        elements = elements,
        revision = 1,
        scrollExtentPx = 200f,
        inlineImagesPreserveScrollLine = inlineImages,
    )

    private fun text(top: Float, bottom: Float, position: Int) = ReaderElement.Text(
        bounds = ReaderRect(0f, top + 10f, 10f, bottom + 10f),
        baselinePx = bottom + 5f,
        value = position.toString(),
        style = style,
        selected = false,
        emphasized = false,
        chapterPosition = position,
    )
}
