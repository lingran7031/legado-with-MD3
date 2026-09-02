@file:Suppress("DEPRECATION")
package io.legado.app.service

import android.app.PendingIntent
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.constant.AppConst
import io.legado.app.constant.AppLog
import io.legado.app.constant.AppPattern
import io.legado.app.domain.gateway.ReadAloudSettingsGateway
import io.legado.app.domain.model.readaloud.ReadAloudPlaybackCursor
import io.legado.app.domain.model.readaloud.ReadAloudVoice
import io.legado.app.domain.model.readaloud.SpeechEngineRoute
import io.legado.app.domain.model.readaloud.SpeechVoiceRouter
import io.legado.app.domain.model.readaloud.SystemTtsVoiceConfig
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.MediaHelp
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.lib.dialogs.SelectItem
import io.legado.app.model.ReadAloud
import io.legado.app.model.ReadBook
import io.legado.app.ui.config.readConfig.ReadConfig
import io.legado.app.utils.GSON
import io.legado.app.utils.LogUtils
import io.legado.app.utils.buildMainHandler
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.servicePendingIntent
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.atomic.AtomicLong

/**
 * 本地朗读
 */
class TTSReadAloudService : BaseReadAloudService(), KoinComponent {

    override val useSpeechPlaybackQueue: Boolean = true

    protected override val currentSpeechRate: Float
        get() = if (ReadConfig.ttsFollowSys) 1f else (speechRateSetting + 5) / 10f

    private val readAloudSettingsGateway: ReadAloudSettingsGateway by inject()
    @Volatile
    private var speechRateSetting: Int = 5

    private var textToSpeech: TextToSpeech? = null
    private var ttsInitFinish = false
    private val ttsUtteranceListener = TTSUtteranceListener()
    private var speakJob: Coroutine<*>? = null
    private var utteranceStartPos = 0
    private var utteranceStartReadAloudNumber = 0
    private var needParagraphInterval = false // 是否需要进行段落间隔延迟
    private var activeEngine = ""
    private var activeVoiceName = ""
    private var defaultVoiceName = ""
    private var initGeneration = 0
    private val playbackSessionId = AtomicLong()
    private val callbackHandler by lazy { buildMainHandler() }
    private val TAG = "TTSReadAloudService"

    override fun onCreate() {
        super.onCreate()
        speechRateSetting = readAloudSettingsGateway.currentSettings.ttsSpeechRate
        lifecycleScope.launch {
            readAloudSettingsGateway.settings.collect {
                speechRateSetting = it.ttsSpeechRate
            }
        }
        initTts()
    }

    override fun onDestroy() {
        super.onDestroy()
        clearTTS()
    }

    @Synchronized
    private fun initTts(engineOverride: String? = null) {
        ttsInitFinish = false
        val engine = engineOverride
            ?: GSON.fromJsonObject<SelectItem<String>>(ReadAloud.ttsEngine).getOrNull()?.value
        activeEngine = engine.orEmpty()
        val generation = ++initGeneration
        LogUtils.d(TAG, "initTts engine:$engine")
        textToSpeech = if (engine.isNullOrBlank()) {
            TextToSpeech(this) { status -> onTtsInitialized(status, generation) }
        } else {
            TextToSpeech(this, { status -> onTtsInitialized(status, generation) }, engine)
        }
        upSpeechRate()
    }

    @Synchronized
    fun clearTTS() {
        playbackSessionId.incrementAndGet()
        textToSpeech?.runCatching {
            stop()
            shutdown()
        }
        textToSpeech = null
        ttsInitFinish = false
        activeVoiceName = ""
        defaultVoiceName = ""
        initGeneration++
    }

    private fun onTtsInitialized(status: Int, generation: Int) {
        if (generation != initGeneration) return
        if (status == TextToSpeech.SUCCESS) {
            textToSpeech?.let {
                it.setOnUtteranceProgressListener(ttsUtteranceListener)
                defaultVoiceName = it.defaultVoice?.name.orEmpty()
                activeVoiceName = defaultVoiceName
                ttsInitFinish = true
                play()
            }
        } else {
            toastOnUi(R.string.tts_init_failed)
        }
    }

    @Synchronized
    override fun play() {
        val sessionId = playbackSessionId.incrementAndGet()
        if (hasSpeechPlaybackQueue) {
            val route = systemVoiceForCurrentCue()
            val requiredEngine = route.engineId
            if (requiredEngine != activeEngine || textToSpeech == null) {
                clearTTS()
                initTts(requiredEngine)
                return
            }
            applyVoice(route.speakerId)
            applyPreset(route)
        }
        if (!ttsInitFinish) return
        if (!requestFocus()) return
        if (contentList.isEmpty()) {
            AppLog.putDebug("朗读列表为空")
            ReadBook.readAloud()
            return
        }
        super.play()
        MediaHelp.playSilentSound(this@TTSReadAloudService)
        
        // 捕获本次是否需要进行段落延迟，并将标志位复位（防多次触发）
        val isDelay = needParagraphInterval
        needParagraphInterval = false
        
        speakJob?.cancel()
        val startSpeak = nowSpeak
        val startParagraphPos = paragraphStartPos
        speakJob = execute {
            val interval = ReadConfig.ttsParagraphInterval.toLong()
            AppLog.putDebug("TTS_PLAY: nowSpeak=$startSpeak, isDelay=$isDelay, interval=$interval")
            
            if (hasSpeechPlaybackQueue || interval > 0) {
                // 段落间隔模式：单段播放
                if (isDelay) {
                    AppLog.putDebug("TTS开始延迟: $interval 毫秒")
                    delay(interval)
                    AppLog.putDebug("TTS延迟结束，准备播放")
                }
                ensureActive()
                if (!isCurrentPlayback(sessionId)) return@execute
                
                LogUtils.d(TAG, "朗读列表大小 ${contentList.size}")
                if (textToSpeech == null) throw NoStackTraceException("tts is null")
                var text = contentList[startSpeak]
                if (startParagraphPos > 0) {
                    text = text.substring(startParagraphPos)
                }
                if (text.matches(AppPattern.notReadAloudRegex)) {
                    AppLog.putDebug("TTS段落全标点跳过: nowSpeak=$startSpeak")
                    ttsUtteranceListener.onDone(ttsUtteranceId(sessionId, startSpeak))
                    return@execute
                }
                AppLog.putDebug("TTS开始Speak: $text")
                val result = speakCurrent(
                    sessionId = sessionId,
                    text = text,
                    queueMode = TextToSpeech.QUEUE_FLUSH,
                    index = startSpeak,
                ) ?: return@execute
                if (result == TextToSpeech.ERROR) {
                    AppLog.put("tts出错 尝试重新初始化")
                    clearTTS()
                    initTts()
                    return@execute
                }
                LogUtils.d(TAG, "朗读内容添加完成")
            } else {
                // 无间隔模式：保持原有的队列式连续播放，确保无缝衔接
                LogUtils.d(TAG, "朗读列表大小 ${contentList.size}")
                LogUtils.d(TAG, "朗读页数 ${readerReadAloudChapter?.pageCount}")
                if (textToSpeech == null) throw NoStackTraceException("tts is null")
                val contentList = contentList
                var isAddedText = false
                for (i in startSpeak until contentList.size) {
                    ensureActive()
                    if (!isCurrentPlayback(sessionId)) return@execute
                    var text = contentList[i]
                    if (startParagraphPos > 0 && i == startSpeak) {
                        text = text.substring(startParagraphPos)
                    }
                    if (text.matches(AppPattern.notReadAloudRegex)) {
                        continue
                    }
                    if (!isAddedText) {
                        val result = speakCurrent(
                            sessionId = sessionId,
                            text = text,
                            queueMode = TextToSpeech.QUEUE_FLUSH,
                            index = i,
                        ) ?: return@execute
                        if (result == TextToSpeech.ERROR) {
                            AppLog.put("tts出错 尝试重新初始化")
                            clearTTS()
                            initTts()
                            return@execute
                        }
                    } else {
                        val result = speakCurrent(
                            sessionId = sessionId,
                            text = text,
                            queueMode = TextToSpeech.QUEUE_ADD,
                            index = i,
                        ) ?: return@execute
                        if (result == TextToSpeech.ERROR) {
                            AppLog.put("tts朗读出错:$text")
                        }
                    }
                    isAddedText = true
                }
                LogUtils.d(TAG, "朗读内容添加完成")
                if (!isAddedText && isCurrentPlayback(sessionId)) {
                    playStop()
                    val stoppedSessionId = playbackSessionId.get()
                    delay(1000)
                    if (stoppedSessionId == playbackSessionId.get()) {
                        completeCurrentChapter()
                    }
                }
            }
        }.onError {
            AppLog.putDebug("TTS协程异常: ${it.localizedMessage}")
        }
    }

    private fun systemVoiceForCurrentCue(): ReadAloudVoice {
        val configured = GSON.fromJsonObject<SelectItem<String>>(ReadAloud.ttsEngine)
            .getOrNull()?.value.orEmpty()
        val fallback = ReadAloudVoice(
            id = "runtime-system:$configured",
            engineType = ReadAloudVoice.ENGINE_SYSTEM,
            engineId = configured,
            speakerId = "",
            displayName = configured,
        )
        val cue = playbackQueue.cues.getOrNull(nowSpeak) ?: return fallback
        return SpeechVoiceRouter.route(
            cue = cue,
            supportedEngineTypes = setOf(ReadAloudVoice.ENGINE_SYSTEM),
            defaultRoute = SpeechEngineRoute(ReadAloudVoice.ENGINE_SYSTEM, configured),
        ).voice ?: fallback
    }

    private fun applyVoice(voiceName: String) {
        val requestedName = voiceName.ifBlank { defaultVoiceName }
        if (requestedName == activeVoiceName) return
        val tts = textToSpeech ?: return
        val voice = tts.voices.orEmpty().firstOrNull { it.name == requestedName }
        if (voice == null) {
            AppLog.putDebug("系统 TTS 音色不可用: $requestedName")
            return
        }
        if (tts.setVoice(voice) == TextToSpeech.SUCCESS) {
            activeVoiceName = requestedName
        } else {
            AppLog.putDebug("系统 TTS 音色切换失败: $requestedName")
        }
    }

    private fun applyPreset(voice: ReadAloudVoice) {
        val config = runCatching {
            GSON.fromJson(voice.traitsJson, SystemTtsVoiceConfig::class.java)
        }.getOrNull() ?: SystemTtsVoiceConfig()
        val globalRate = if (ReadConfig.ttsFollowSys) {
            1f
        } else {
            (ReadConfig.ttsSpeechRate + 5) / 10f
        }
        textToSpeech?.apply {
            setSpeechRate(config.speechRate ?: globalRate)
            setPitch(config.pitch ?: 1f)
        }
    }

    @Synchronized
    override fun playStop() {
        playbackSessionId.incrementAndGet()
        speakJob?.cancel()
        textToSpeech?.runCatching {
            stop()
        }
    }

    /**
     * 更新朗读速度
     */
    override fun upSpeechRate(reset: Boolean) {
        if (ReadConfig.ttsFollowSys) {
            if (reset) {
                clearTTS()
                initTts()
            }
        } else {
            val speechRate = (speechRateSetting + 5) / 10f
            textToSpeech?.setSpeechRate(speechRate)
            upMediaMetadata()
            if (reset && !pause) {
                play()
            }
        }
    }

    /**
     * 暂停朗读
     */
    override fun pauseReadAloud(abandonFocus: Boolean) {
        super.pauseReadAloud(abandonFocus)
        playStop()
    }

    /**
     * 恢复朗读
     */
    override fun resumeReadAloud() {
        super.resumeReadAloud()
        play()
    }

    /**
     * 朗读监听
     */
    private inner class TTSUtteranceListener : UtteranceProgressListener() {

        private val TAG = "TTSUtteranceListener"

        override fun onStart(s: String) {
            dispatchCurrentCallback(s) {
                LogUtils.d(TAG, "onStart nowSpeak:$nowSpeak pageIndex:$pageIndex utteranceId:$s")
                utteranceStartPos = paragraphStartPos
                utteranceStartReadAloudNumber = readAloudNumber
                readerReadAloudChapter?.let {
                    if (contentList[nowSpeak].matches(AppPattern.notReadAloudRegex)) {
                        nextParagraph(naturalCompletion = true)
                    }
                    if (pageIndex + 1 < it.pageCount
                        && readAloudNumber + 1 > it.pageStart(pageIndex + 1)
                    ) {
                        pageIndex++
                        // This is the TTS engine advancing across a page boundary, not a user turn.
                        // Mark it so ReadBook neither detaches the session nor restarts TTS at page two.
                        withSpeechNavigation { ReadBook.moveToNextPage() }
                    }
                    upTtsProgress(readAloudNumber + 1)
                    upMediaMetadata(showContent = true)
                }
            }
        }

        override fun onDone(s: String) {
            dispatchCurrentCallback(s) {
                LogUtils.d(TAG, "onDone utteranceId:$s")
                continueAfterProgressCallback()
            }
        }

        override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
            super.onRangeStart(utteranceId, start, end, frame)
            dispatchCurrentCallback(utteranceId) {
                paragraphStartPos = utteranceStartPos + start
                // 正在朗读的精确章内位置（段起点 + 段内偏移），保持 readAloudNumber 的"段起点"语义不被污染
                val position = currentRangePosition(utteranceStartReadAloudNumber, start)
                updateReadAloudProgressSnapshot(position)
                val msg =
                    "onRangeStart nowSpeak:$nowSpeak pageIndex:$pageIndex utteranceId:$utteranceId start:$start end:$end frame:$frame"
                LogUtils.d(TAG, msg)
                if (moveToReadAloudPage(position)) {
                    upTtsProgress(position)
                }
            }
        }

        override fun onError(utteranceId: String?, errorCode: Int) {
            dispatchCurrentCallback(utteranceId) {
                LogUtils.d(
                    TAG,
                    "onError nowSpeak:$nowSpeak pageIndex:$pageIndex utteranceId:$utteranceId errorCode:$errorCode"
                )
                continueAfterProgressCallback()
            }
        }

        private fun continueAfterProgressCallback() {
            val hasNextParagraph = nextParagraph(naturalCompletion = true)
            if (shouldContinueTtsPlayback(
                    hasNextParagraph = hasNextParagraph,
                    paused = pause,
                    usesSingleUtteranceQueue = hasSpeechPlaybackQueue,
                    paragraphIntervalMillis = ReadConfig.ttsParagraphInterval,
                )
            ) {
                needParagraphInterval = ReadConfig.ttsParagraphInterval > 0
                play()
            }
        }

        private fun nextParagraph(naturalCompletion: Boolean = false): Boolean {
            if (hasSpeechPlaybackQueue) {
                val current = playbackCursor
                    ?: ReadAloudPlaybackCursor(nowSpeak, paragraphStartPos)
                val next = playbackQueue.next(current)
                if (next != null) {
                    moveToPlaybackCursor(next)
                    return true
                }
                if (naturalCompletion) completeCurrentChapter() else nextChapter()
                return false
            }
            //跳过全标点段落
            do {
                readAloudNumber = nextParagraphPosition(
                    currentPosition = readAloudNumber,
                    paragraphLength = contentList[nowSpeak].length,
                    paragraphStartPosition = paragraphStartPos,
                )
                paragraphStartPos = 0
                nowSpeak++
                if (nowSpeak >= contentList.size) {
                    if (naturalCompletion) completeCurrentChapter() else nextChapter()
                    return false
                }
            } while (contentList[nowSpeak].matches(AppPattern.notReadAloudRegex))
            // 页内切段不引入换行符，累加会漂移，用段落绝对位置重算
            paragraphChapterPositionAt(nowSpeak)?.let { readAloudNumber = it }
            return true
        }

        @Deprecated("Deprecated in Java")
        override fun onError(s: String) {
            dispatchCurrentCallback(s) {
                LogUtils.d(TAG, "onError nowSpeak:$nowSpeak pageIndex:$pageIndex s:$s")
                continueAfterProgressCallback()
            }
        }

    }

    private fun speakCurrent(
        sessionId: Long,
        text: String,
        queueMode: Int,
        index: Int,
    ): Int? = synchronized(this) {
        if (!isCurrentPlayback(sessionId)) return@synchronized null
        val tts = textToSpeech ?: return@synchronized TextToSpeech.ERROR
        tts.runCatching {
            speak(text, queueMode, null, ttsUtteranceId(sessionId, index))
        }.getOrElse {
            AppLog.put("tts出错\n${it.localizedMessage}", it, true)
            TextToSpeech.ERROR
        }
    }

    private fun isCurrentPlayback(sessionId: Long): Boolean =
        sessionId == playbackSessionId.get()

    private fun dispatchCurrentCallback(utteranceId: String?, block: () -> Unit) {
        val currentSessionId = playbackSessionId.get()
        if (!isCurrentTtsPlaybackCallback(utteranceId, currentSessionId)) return
        callbackHandler.post {
            synchronized(this) {
                if (isCurrentTtsPlaybackCallback(utteranceId, playbackSessionId.get())) block()
            }
        }
    }

    override fun aloudServicePendingIntent(actionStr: String): PendingIntent? {
        return servicePendingIntent<TTSReadAloudService>(actionStr)
    }

}

internal fun nextParagraphPosition(
    currentPosition: Int,
    paragraphLength: Int,
    paragraphStartPosition: Int,
): Int = currentPosition + paragraphLength + 1 - paragraphStartPosition

internal fun currentRangePosition(
    utteranceStartPosition: Int,
    rangeStart: Int,
): Int = utteranceStartPosition + rangeStart

internal fun ttsUtteranceId(sessionId: Long, index: Int): String =
    "${AppConst.APP_TAG}:$sessionId:$index"

internal fun ttsPlaybackSessionId(utteranceId: String?): Long? {
    val prefix = "${AppConst.APP_TAG}:"
    return utteranceId
        ?.takeIf { it.startsWith(prefix) }
        ?.substringAfter(prefix)
        ?.substringBefore(':')
        ?.toLongOrNull()
}

internal fun isCurrentTtsPlaybackCallback(
    utteranceId: String?,
    currentSessionId: Long,
): Boolean = ttsPlaybackSessionId(utteranceId) == currentSessionId

internal fun shouldContinueTtsPlayback(
    hasNextParagraph: Boolean,
    paused: Boolean,
    usesSingleUtteranceQueue: Boolean,
    paragraphIntervalMillis: Int,
): Boolean = hasNextParagraph && !paused &&
    (usesSingleUtteranceQueue || paragraphIntervalMillis > 0)
