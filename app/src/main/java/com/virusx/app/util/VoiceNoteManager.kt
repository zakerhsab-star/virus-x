package com.virusx.app.util

import android.content.Context
import android.content.Intent
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class RecordedVoiceNoteResult(
    val file: File,
    val durationSeconds: Int,
    val waveform: List<Float>
)

@Singleton
class VoiceNoteManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var mediaRecorder: MediaRecorder? = null
    private var currentOutputFile: File? = null
    private var recordingStartTimeMs: Long = 0L
    private var amplitudeSamplingJob: Job? = null
    private val collectedAmplitudes = mutableListOf<Float>()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _liveWaveform = MutableStateFlow<List<Float>>(emptyList())
    val liveWaveform: StateFlow<List<Float>> = _liveWaveform.asStateFlow()

    private val _recordingDurationSeconds = MutableStateFlow(0)
    val recordingDurationSeconds: StateFlow<Int> = _recordingDurationSeconds.asStateFlow()

    private val _currentlyPlayingMessageId = MutableStateFlow<String?>(null)
    val currentlyPlayingMessageId: StateFlow<String?> = _currentlyPlayingMessageId.asStateFlow()

    private val _isTranscribing = MutableStateFlow(false)
    val isTranscribing: StateFlow<Boolean> = _isTranscribing.asStateFlow()

    private var exoPlayer: ExoPlayer? = null

    fun startRecording(scope: CoroutineScope): Result<Unit> = runCatching {
        stopRecording()

        val voiceDir = File(context.cacheDir, "voice_notes").apply { mkdirs() }
        val outputFile = File(voiceDir, "vn_${System.currentTimeMillis()}.m4a")
        currentOutputFile = outputFile
        collectedAmplitudes.clear()

        val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

        recorder.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(128000)
            setAudioSamplingRate(44100)
            setOutputFile(outputFile.absolutePath)
            prepare()
            start()
        }

        mediaRecorder = recorder
        recordingStartTimeMs = System.currentTimeMillis()
        _isRecording.value = true
        _recordingDurationSeconds.value = 0
        _liveWaveform.value = emptyList()

        amplitudeSamplingJob = scope.launch(Dispatchers.Default) {
            while (isActive && _isRecording.value) {
                val maxAmp = runCatching { mediaRecorder?.maxAmplitude ?: 0 }.getOrDefault(0)
                val normalized = (maxAmp / 32767f).coerceIn(0.08f, 1.0f)
                collectedAmplitudes.add(normalized)
                _liveWaveform.value = collectedAmplitudes.takeLast(40)
                _recordingDurationSeconds.value =
                    ((System.currentTimeMillis() - recordingStartTimeMs) / 1000L).toInt()
                delay(120L)
            }
        }
    }

    fun stopRecording(): RecordedVoiceNoteResult? {
        amplitudeSamplingJob?.cancel()
        amplitudeSamplingJob = null
        _isRecording.value = false

        val recorder = mediaRecorder ?: return null
        mediaRecorder = null

        runCatching {
            recorder.stop()
            recorder.release()
        }

        val file = currentOutputFile ?: return null
        if (!file.exists() || file.length() == 0L) return null

        val durationSec = ((System.currentTimeMillis() - recordingStartTimeMs) / 1000L)
            .toInt()
            .coerceAtLeast(1)

        val finalWaveform = if (collectedAmplitudes.isEmpty()) {
            List(24) { 0.25f }
        } else {
            downsampleWaveform(collectedAmplitudes, targetBars = 32)
        }

        return RecordedVoiceNoteResult(
            file = file,
            durationSeconds = durationSec,
            waveform = finalWaveform
        )
    }

    fun playVoiceNote(
        messageId: String,
        mediaUrl: String,
        pitchFilter: String?,
        onCompleted: (String) -> Unit
    ) {
        stopPlayback()

        val player = ExoPlayer.Builder(context).build()
        exoPlayer = player
        _currentlyPlayingMessageId.value = messageId

        val playbackParams = when (pitchFilter) {
            "deep" -> PlaybackParameters(0.92f, 0.72f)
            "robot" -> PlaybackParameters(1.08f, 1.35f)
            "fast" -> PlaybackParameters(1.45f, 1.05f)
            else -> PlaybackParameters(1.0f, 1.0f)
        }

        player.playbackParameters = playbackParams
        player.setMediaItem(MediaItem.fromUri(mediaUrl))
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    val finishedId = _currentlyPlayingMessageId.value
                    stopPlayback()
                    if (finishedId != null) {
                        onCompleted(finishedId)
                    }
                }
            }
        })
        player.prepare()
        player.play()
    }

    fun stopPlayback() {
        _currentlyPlayingMessageId.value = null
        exoPlayer?.let { player ->
            runCatching {
                player.stop()
                player.release()
            }
        }
        exoPlayer = null
    }

    fun recognizeSpeechToText(
        languageTag: String = "ar-EG",
        onResult: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onError("خدمة التعرف الصوتي غير متوفرة على هذا الجهاز")
            return
        }

        val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }

        _isTranscribing.value = true

        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                _isTranscribing.value = false
            }

            override fun onError(error: Int) {
                _isTranscribing.value = false
                recognizer.destroy()
                onError("تعذر تحويل الصوت لنص (كود: $error)")
            }

            override fun onResults(results: Bundle?) {
                _isTranscribing.value = false
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull().orEmpty()
                recognizer.destroy()
                if (text.isNotBlank()) {
                    onResult(text)
                } else {
                    onError("لم يتم التقاط نص واضح")
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        recognizer.startListening(intent)
    }

    private fun downsampleWaveform(source: List<Float>, targetBars: Int): List<Float> {
        if (source.size <= targetBars) return source
        val step = source.size.toFloat() / targetBars
        return List(targetBars) { index ->
            val pos = (index * step).toInt().coerceIn(0, source.lastIndex)
            source[pos]
        }
    }
}
