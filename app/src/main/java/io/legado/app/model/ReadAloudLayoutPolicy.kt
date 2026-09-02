package io.legado.app.model

internal fun shouldRestartReadAloudAfterContentLoad(
    preserveReadAloudPosition: Boolean,
    serviceChapterIndex: Int,
    loadedChapterIndex: Int,
): Boolean = !preserveReadAloudPosition || serviceChapterIndex != loadedChapterIndex

internal fun activeReadAloudProgress(
    isPlaying: Boolean,
    currentProgress: Int,
): Int? = currentProgress.takeIf { isPlaying && it > 0 }

internal fun shouldSyncReadAloudAfterPagination(
    isReadAloudRunning: Boolean,
    serviceChapterIndex: Int,
    publishedChapterIndexes: Collection<Int>,
): Boolean = isReadAloudRunning && serviceChapterIndex in publishedChapterIndexes
