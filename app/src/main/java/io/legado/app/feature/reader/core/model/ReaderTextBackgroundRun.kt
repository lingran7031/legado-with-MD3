package io.legado.app.feature.reader.core.model

import kotlin.math.abs

data class ReaderTextBackgroundRun(
    val bounds: ReaderRect,
    val contentBounds: ReaderRect,
    val image: ReaderTextBackgroundImage,
)

fun ReaderPage.textBackgroundRuns(): List<ReaderTextBackgroundRun> {
    val runs = mutableListOf<ReaderTextBackgroundRun>()
    elements.filterIsInstance<ReaderElement.Text>().forEach { text ->
        val image = text.style.backgroundImage ?: return@forEach
        val previous = runs.lastOrNull()
        if (
            previous != null && previous.image == image &&
            abs(previous.contentBounds.top - text.bounds.top) < 0.5f &&
            abs(previous.contentBounds.bottom - text.bounds.bottom) < 0.5f &&
            abs(previous.contentBounds.right - text.bounds.left) < 1f
        ) {
            runs[runs.lastIndex] = previous.copy(
                bounds = previous.bounds.copy(
                    right = text.bounds.right,
                    top = minOf(previous.bounds.top, text.bounds.top - text.backgroundFrameTopPx),
                    bottom = maxOf(previous.bounds.bottom, text.bounds.bottom + text.backgroundFrameBottomPx),
                ),
                contentBounds = previous.contentBounds.copy(right = text.bounds.right),
            )
        } else {
            runs += ReaderTextBackgroundRun(
                bounds = text.bounds.copy(
                    top = text.bounds.top - text.backgroundFrameTopPx,
                    bottom = text.bounds.bottom + text.backgroundFrameBottomPx,
                ),
                contentBounds = text.bounds,
                image = image,
            )
        }
    }
    return runs.map { run ->
        if (run.image.fit != 3) run else run.copy(
            bounds = run.bounds.copy(
                left = run.bounds.left - run.image.contentInsetLeftPx,
                right = run.bounds.right + run.image.contentInsetRightPx,
            ),
        )
    }
}
