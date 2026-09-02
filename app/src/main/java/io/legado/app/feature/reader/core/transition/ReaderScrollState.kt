package io.legado.app.feature.reader.core.transition

import io.legado.app.feature.reader.core.model.ReaderPageId
import io.legado.app.feature.reader.core.model.ReaderElement
import io.legado.app.feature.reader.core.model.ReaderPage

enum class ReaderScrollCrossing { PREVIOUS, NEXT }

data class ReaderScrollResult(
    val offsetPx: Float,
    val crossing: ReaderScrollCrossing? = null,
    val hitBoundary: Boolean = false,
)

/** One-frame continuous-scroll reducer. Page replacement is performed by the host after crossing. */
object ReaderScrollPolicy {
    /** Do not reduce another frame against the old page after a crossing requested replacement. */
    fun canApplyDelta(pendingPageTarget: ReaderPageId?): Boolean = pendingPageTarget == null

    /**
     * Click paging keeps one visible text row for context, matching ScrollPageDelegate.
     * Non-inline image pages and empty pages move by one full viewport.
     */
    fun pageStep(
        page: ReaderPage,
        offsetPx: Float,
        direction: ReaderTurnDirection,
    ): Float {
        val viewport = (page.contentBottomPx - page.contentTopPx).coerceAtLeast(1f)
        val visible = page.elements.filter { element ->
            element.bounds.bottom + offsetPx > page.contentTopPx &&
                element.bounds.top + offsetPx < page.contentBottomPx
        }
        val text = visible.filterIsInstance<ReaderElement.Text>()
        if (text.isEmpty() ||
            (!page.inlineImagesPreserveScrollLine && visible.any { it is ReaderElement.Image })
        ) return if (direction == ReaderTurnDirection.PREVIOUS) viewport else -viewport

        val distance = when (direction) {
            ReaderTurnDirection.NEXT ->
                text.maxOf { it.bounds.top } + offsetPx - page.contentTopPx
            ReaderTurnDirection.PREVIOUS ->
                viewport - (text.minOf { it.bounds.bottom } + offsetPx - page.contentTopPx)
        }.coerceIn(0f, viewport)
        val effective = distance.takeIf { it > 0f } ?: viewport
        return if (direction == ReaderTurnDirection.PREVIOUS) effective else -effective
    }

    fun offsetAfterPageChange(
        offsetPx: Float,
        carriedTarget: ReaderPageId?,
        currentPage: ReaderPageId,
    ): Float = if (carriedTarget == currentPage) offsetPx else 0f

    fun apply(
        offsetPx: Float,
        deltaPx: Float,
        previousExtentPx: Float,
        currentExtentPx: Float,
        viewportExtentPx: Float,
        hasPrevious: Boolean,
        hasNext: Boolean,
    ): ReaderScrollResult {
        if (currentExtentPx <= 0f) return ReaderScrollResult(0f)
        val next = offsetPx + deltaPx
        if (next > 0f) {
            return if (hasPrevious && previousExtentPx > 0f) {
                ReaderScrollResult(next - previousExtentPx, ReaderScrollCrossing.PREVIOUS)
            } else ReaderScrollResult(0f, hitBoundary = true)
        }
        if (!hasNext && next < 0f && next + currentExtentPx < viewportExtentPx) {
            return ReaderScrollResult(
                offsetPx = minOf(0f, viewportExtentPx - currentExtentPx),
                hitBoundary = true,
            )
        }
        if (next < -currentExtentPx) {
            return if (hasNext) {
                ReaderScrollResult(next + currentExtentPx, ReaderScrollCrossing.NEXT)
            } else {
                val bottom = minOf(0f, viewportExtentPx - currentExtentPx)
                ReaderScrollResult(bottom, hitBoundary = true)
            }
        }
        return ReaderScrollResult(next)
    }
}
