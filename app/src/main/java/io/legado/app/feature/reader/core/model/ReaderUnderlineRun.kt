package io.legado.app.feature.reader.core.model

import kotlin.math.abs

data class ReaderUnderlineRun(
    val bounds: ReaderRect,
    val underline: ReaderUnderline,
)

/** Coalesces adjacent styled text exactly once per visual line. */
fun ReaderPage.underlineRuns(): List<ReaderUnderlineRun> {
    val runs = mutableListOf<ReaderUnderlineRun>()
    elements.filterIsInstance<ReaderElement.Text>().forEach { text ->
        val underline = text.style.underline ?: return@forEach
        val previous = runs.lastOrNull()
        if (
            previous != null && previous.underline == underline &&
            abs(previous.bounds.top - text.bounds.top) < 0.5f &&
            abs(previous.bounds.bottom - text.bounds.bottom) < 0.5f &&
            abs(previous.bounds.right - text.bounds.left) < 1f
        ) {
            runs[runs.lastIndex] = previous.copy(
                bounds = previous.bounds.copy(right = text.bounds.right),
            )
        } else {
            runs += ReaderUnderlineRun(text.bounds, underline)
        }
    }
    return runs
}
