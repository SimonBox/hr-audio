package dev.hraudio

import android.content.Context
import android.media.*
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

internal interface Speaker {
    val failed: Boolean
    fun say(text: String)
    fun close()
}

internal class Voice(context: Context) : Speaker {
    private val main = Handler(Looper.getMainLooper())
    private val audio = context.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(attributes).setOnAudioFocusChangeListener { change ->
            if (change < 0) { engine?.stop(); release() }
        }.build()
    private var currentId: String? = null
    private var sequence = 0
    private var pending: Pair<String, Long>? = null
    private var ready = false
    override var failed = false
        private set
    private var closed = false
    private var engine: TextToSpeech? = null

    init {
        engine = TextToSpeech(context.applicationContext) { status ->
            main.post {
                if (!closed) {
                    ready = status == TextToSpeech.SUCCESS &&
                        (engine?.setLanguage(Locale.US) ?: -1) >= 0
                    failed = !ready
                    engine?.setAudioAttributes(attributes)
                    engine?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(id: String?) {}
                        override fun onDone(id: String?) = finish(id)
                        @Deprecated("Legacy callback") override fun onError(id: String?) = finish(id)
                    })
                    val next = pending
                    pending = null
                    if (ready && next != null && SystemClock.elapsedRealtime() - next.second < 8_000) say(next.first)
                }
            }
        }
    }

    override fun say(text: String) {
        if (!ready) { pending = text to SystemClock.elapsedRealtime(); return }
        if (audio.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return
        val id = (++sequence).toString()
        currentId = id
        if (engine?.speak(text, TextToSpeech.QUEUE_FLUSH, null, id) != TextToSpeech.SUCCESS) release()
    }

    private fun finish(id: String?) { main.post { if (!closed && currentId == id) release() } }
    private fun release() {
        if (currentId != null) audio.abandonAudioFocusRequest(focus)
        currentId = null
    }
    override fun close() {
        closed = true
        engine?.stop()
        engine?.shutdown()
        release()
    }
}
