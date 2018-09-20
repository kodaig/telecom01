package com.example.rondo.telecom01

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.ToggleButton

import java.io.UnsupportedEncodingException
import java.util.Arrays

class SendActivity : AppCompatActivity(), Runnable, View.OnClickListener, Handler.Callback {

    private var mHandler: Handler? = null
    private var mAudioTrack: AudioTrack? = null
    private var sendBtn: ToggleButton? = null
    private var mEditText01: EditText? = null
    private val mPlayBuf = ShortArray(SAMPLE_RATE)
    private val mSignals = Array(ELMS_MAX) { ShortArray(SAMPLE_RATE / 10) }
    private var mText: String? = null

    // サイン波データを生成
    private fun createSineWave(buf: ShortArray, freq: Int, amplitude: Int, doClear: Boolean) {
        if (doClear) {
            Arrays.fill(buf, 0.toShort())
        }
        for (i in buf.indices) {
            val currentSec = i * SEC_PER_SAMPLEPOINT // 現在位置の経過秒数
            val `val` = amplitude * Math.sin(2.0 * Math.PI * freq.toDouble() * currentSec.toDouble())
            buf[i] = `val`.toShort()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mHandler = Handler(this)
        setContentView(R.layout.activity_send)
        sendBtn = findViewById<ToggleButton>(R.id.sendBtn)
        sendBtn!!.setOnClickListener(this)
        mEditText01 = findViewById<EditText>(R.id.sendEditText)

        val bufferSizeInBytes = AudioRecord.getMinBufferSize(SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT)

        // 先頭・終端の目印用信号データ
        createSineWave(mPlayBuf, FREQ_KEY, AMP, true)

        // 256種類の信号データを生成
        for (i in 0 until ELMS_MAX) {
            createSineWave(mSignals[i], (FREQ_BASE + FREQ_STEP * i).toShort().toInt(), AMP, true)
        }

        // 再生用
        mAudioTrack = AudioTrack(AudioManager.STREAM_MUSIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSizeInBytes,
                AudioTrack.MODE_STREAM)
    }

    public override fun onStop() {
        super.onStop()
    }

    public override fun onDestroy() {
        super.onDestroy()
        try {
            Thread.sleep(2000)
        } catch (e: InterruptedException) {
        }

        if (mAudioTrack != null) {
            if (mAudioTrack!!.playState != AudioTrack.PLAYSTATE_STOPPED) {
                mAudioTrack!!.stop()
                mAudioTrack!!.flush()
            }
            mAudioTrack = null
        }
    }

    override fun onClick(v: View) {
        if (mAudioTrack!!.playState != AudioTrack.PLAYSTATE_STOPPED) {
            mAudioTrack!!.stop()
            mAudioTrack!!.flush()
        }
        if (sendBtn!!.isChecked) {
            mText = mEditText01!!.text.toString()
            if (mText!!.length > 0) {
                Thread(this).start()
            } else {
                sendBtn!!.isChecked = false
            }
        }
    }

    override fun handleMessage(msg: Message): Boolean {
        when (msg.what) {
            MSG_PLAY_END -> {
                sendBtn!!.isChecked = false
            }
        }
        return true
    }

    override fun run() {
        mHandler!!.sendEmptyMessage(MSG_PLAY_START)
        var strByte: ByteArray? = null
        try {
            strByte = mText!!.toByteArray(charset("UTF-8"))
        } catch (e: UnsupportedEncodingException) {
        }

        mAudioTrack!!.play()
        mAudioTrack!!.write(mPlayBuf, 0, ELMS_1SEC/5) // 開始
        for (i in strByte!!.indices) {
            valueToWave(strByte[i])
        }
        mAudioTrack!!.write(mPlayBuf, 0, ELMS_1SEC/5) // 終端

        mAudioTrack!!.stop()
        mAudioTrack!!.flush()
        mHandler!!.sendEmptyMessage(MSG_PLAY_END)
    }

    // 指定されたバイト値を音声信号に置き換えて再生する
    private fun valueToWave(`val`: Byte) {
        mAudioTrack!!.write(mSignals[`val`.toInt()], 0, ELMS_100MSEC)
    }

    companion object {

        private val SAMPLE_RATE = 44100
        private val SEC_PER_SAMPLEPOINT = 1.0f / SAMPLE_RATE
        private val AMP = 4000
        private val FREQ_BASE = 16000
        private val FREQ_STEP = 20
        private val FREQ_KEY = FREQ_BASE - 20
        private val ELMS_1SEC = SAMPLE_RATE
        private val ELMS_100MSEC = SAMPLE_RATE / 10
        private val ELMS_MAX = 256

        private val MSG_PLAY_START = 120
        private val MSG_PLAY_END = 130
    }
}

