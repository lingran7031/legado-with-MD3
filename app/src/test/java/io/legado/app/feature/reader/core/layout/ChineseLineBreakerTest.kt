package io.legado.app.feature.reader.core.layout

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class ChineseLineBreakerTest {
    private fun breakText(words: List<String>, widthPx: Int) = ChineseLineBreaker(
        words, List(words.size) { 10f }, 0, widthPx, 10f, 0f,
    )

    @Test fun normalBreak() {
        val result = breakText(listOf("我", "是", "一", "二", "三"), 25)
        assertEquals(3, result.lineCount)
        assertArrayEquals(intArrayOf(0, 2, 4, 5), result.lineStarts)
        assertArrayEquals(floatArrayOf(20f, 20f, 10f), result.lineWidthsPx, 0f)
    }

    @Test fun closingPunctuationDoesNotStartLine() {
        val result = breakText(listOf("我", "是", "，", "三"), 25)
        assertEquals(3, result.lineCount)
        assertArrayEquals(intArrayOf(0, 1, 3, 4), result.lineStarts)
        assertArrayEquals(floatArrayOf(10f, 20f, 10f), result.lineWidthsPx, 0f)
    }
}
