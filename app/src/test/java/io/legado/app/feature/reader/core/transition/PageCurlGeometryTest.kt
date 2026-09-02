package io.legado.app.feature.reader.core.transition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PageCurlGeometryTest {
    @Test fun curlShadowColorsMatchLegacyGradientDrawables() {
        assertEquals(0xFF111111.toInt(), ReaderCurlVisualPolicy.backShadowDarkArgb)
        assertEquals(0x80111111.toInt(), ReaderCurlVisualPolicy.frontShadowDarkArgb)
        assertEquals(0xB0333333.toInt(), ReaderCurlVisualPolicy.folderShadowDarkArgb)
    }

    @Test fun choosesTouchedCornerAndProducesFiniteBezierFrame() {
        val frame = PageCurlGeometry.calculate(1080f, 1920f, 800f, 1400f)
        assertNotNull(frame)
        frame!!
        assertEquals(CurlPoint(1080f, 1920f), frame.corner)
        assertTrue(frame.isValid)
        assertTrue(frame.touchToCornerDistance > 0f)
    }

    @Test fun mirrorLinearPartIsAReflection() {
        val frame = PageCurlGeometry.calculate(1080f, 1920f, 700f, 1300f)
        assertNotNull(frame)
        val mirror = frame!!.mirror
        val determinant = mirror.scaleX * mirror.scaleY - mirror.skewX * mirror.skewY
        assertEquals(-1f, determinant, .001f)
    }

    @Test fun invalidViewportHasNoFrame() {
        assertEquals(null, PageCurlGeometry.calculate(0f, 1920f, 1f, 1f))
    }

    @Test fun legacyMiddleZonesChooseStableCurlCorners() {
        val height = 900f
        assertEquals(height, ReaderCurlTouchPolicy.dragY(ReaderTurnDirection.PREVIOUS, 100f, 120f, height))
        assertEquals(1f, ReaderCurlTouchPolicy.dragY(ReaderTurnDirection.NEXT, 350f, 430f, height))
        assertEquals(height, ReaderCurlTouchPolicy.dragY(ReaderTurnDirection.NEXT, 500f, 430f, height))
        assertEquals(100f, ReaderCurlTouchPolicy.dragY(ReaderTurnDirection.NEXT, 100f, 100f, height))
    }

    @Test fun programmaticSimulationUsesLegacyTopAndBottomAnchors() {
        assertEquals(900f, ReaderCurlTouchPolicy.programmaticY(ReaderTurnDirection.PREVIOUS, 20f, 900f))
        assertEquals(1f, ReaderCurlTouchPolicy.programmaticY(ReaderTurnDirection.NEXT, 200f, 900f))
        assertEquals(810f, ReaderCurlTouchPolicy.programmaticY(ReaderTurnDirection.NEXT, 700f, 900f))
    }

    @Test fun programmaticSimulationUsesLegacyHorizontalTouchAnchors() {
        assertEquals(0f, ReaderCurlTouchPolicy.programmaticX(ReaderTurnDirection.PREVIOUS, 1000f))
        assertEquals(900f, ReaderCurlTouchPolicy.programmaticX(ReaderTurnDirection.NEXT, 1000f))
    }

    @Test fun simulationSettleCrossesTheLegacyHorizontalViewportBoundary() {
        assertEquals(1000f, ReaderCurlTouchPolicy.settledX(ReaderTurnDirection.PREVIOUS, true, 1000f))
        assertEquals(-1000f, ReaderCurlTouchPolicy.settledX(ReaderTurnDirection.PREVIOUS, false, 1000f))
        assertEquals(-1000f, ReaderCurlTouchPolicy.settledX(ReaderTurnDirection.NEXT, true, 1000f))
        assertEquals(1000f, ReaderCurlTouchPolicy.settledX(ReaderTurnDirection.NEXT, false, 1000f))
    }

    @Test fun simulationSettleDurationUsesItsFullLegacyCurlTravel() {
        assertEquals(570, ReaderCurlTouchPolicy.settleDurationMillis(900f, -1000f, 1000f))
        assertEquals(300, ReaderCurlTouchPolicy.settleDurationMillis(0f, 1000f, 1000f))
        assertEquals(0, ReaderCurlTouchPolicy.settleDurationMillis(1000f, 1000f, 1000f))
    }

    @Test fun simulationCornerStaysLockedWhileTouchCrossesTheViewportMidpoint() {
        val corner = CurlPoint(1080f, 0f)
        val first = PageCurlGeometry.calculate(1080f, 1920f, 900f, 300f, corner)!!
        val crossed = PageCurlGeometry.calculate(1080f, 1920f, 300f, 100f, corner)!!
        assertEquals(corner, first.corner)
        assertEquals(corner, crossed.corner)
    }

    @Test fun curlSettleAnchorMatchesTheCapturedLegacyCorner() {
        assertEquals(0f, ReaderCurlTouchPolicy.cornerY(ReaderTurnDirection.NEXT, 200f, 900f))
        assertEquals(900f, ReaderCurlTouchPolicy.cornerY(ReaderTurnDirection.NEXT, 700f, 900f))
        assertEquals(900f, ReaderCurlTouchPolicy.cornerY(ReaderTurnDirection.PREVIOUS, 200f, 900f))
        assertEquals(1f, ReaderCurlTouchPolicy.settledY(0f, 900f))
        assertEquals(900f, ReaderCurlTouchPolicy.settledY(900f, 900f))
    }
}
