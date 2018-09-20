package com.example.rondo.telecom01

import org.jtransforms.fft.DoubleFFT_1D
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView

import java.io.UnsupportedEncodingException

class ReceiveActivity : AppCompatActivity(), Runnable, View.OnClickListener, Handler.Callback {

    private var mHandler: Handler? = null
    private var mAudioRecord: AudioRecord? = null

    private var receiveBtn: Button? = null
    private var receiveTextView: TextView? = null

    private var mInRecording = false
    private var mStop = false
    private var mBufferSizeInShort: Int = 0

    private var mRecordBuf: ShortArray? = null
    private var mTestBuf: ShortArray? = null
    private var mFFT: DoubleFFT_1D? = null
    private var mFFTBuffer: DoubleArray? = null
    private var mFFTSize: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mHandler = Handler(this)
        setContentView(R.layout.activity_receive)

        receiveBtn = findViewById<Button>(R.id.receiveBtn)
        receiveBtn!!.setOnClickListener(this)
        receiveTextView = findViewById<TextView>(R.id.messageTextView)
        receiveTextView!!.setOnClickListener(this)

        val bufferSizeInBytes = AudioRecord.getMinBufferSize(SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT)

        mBufferSizeInShort = bufferSizeInBytes / 2
        // 集音用バッファ
        mRecordBuf = ShortArray(mBufferSizeInShort)

        // FFT 処理用
        mTestBuf = ShortArray(UNITSIZE)
        mFFTSize = UNITSIZE
        mFFT = DoubleFFT_1D(mFFTSize.toLong())
        mFFTBuffer = DoubleArray(mFFTSize)

        mAudioRecord = AudioRecord(MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSizeInBytes)
    }

    public override fun onStop() {
        super.onStop()
    }

    public override fun onDestroy() {
        super.onDestroy()
        mStop = true
        try {
            Thread.sleep(2000)
        } catch (e: InterruptedException) {
        }

        if (mAudioRecord != null) {
            if (mAudioRecord!!.recordingState != AudioRecord.RECORDSTATE_STOPPED) {
                mAudioRecord!!.stop()
            }
            mAudioRecord = null
        }
    }

    override fun onClick(v: View) {
        if (v === receiveBtn as View?) {
            // 集音開始 or 終了
            if (!mInRecording) {
                mInRecording = true
                Thread(this).start()
            } else {
                mInRecording = false
            }
        } else if (v === receiveTextView as View?) {
            // 表示データをクリア
            receiveTextView!!.text = ""
        }
        return
    }

    override fun handleMessage(msg: Message): Boolean {
        when (msg.what) {
            MSG_RECORD_START -> {
                receiveBtn!!.text = "STOP"
            }
            MSG_RECORD_END -> {
                receiveBtn!!.text = "START"
            }
            MSG_DATA_RECV -> {
                val ch = byteArrayOf(msg.arg1.toByte())
                try {
                    // 受信データを表示
                    var s = ch.toString(Charsets.UTF_8)
                    s = receiveTextView!!.text.toString() + s
                    receiveTextView!!.text = s
                } catch (e: UnsupportedEncodingException) {
                }

            }
        }
        return true
    }

    override fun run() {
        var dataCount = 0
        var bSilence = false
        mHandler!!.sendEmptyMessage(MSG_RECORD_START)
        // 集音開始
        mAudioRecord!!.startRecording()
        while (mInRecording && !mStop) {
            // 音声データ読み込み
            mAudioRecord!!.read(mRecordBuf!!, 0, mBufferSizeInShort)
            bSilence = true
            for (i in 0 until mBufferSizeInShort) {
                val s = mRecordBuf!![i]
                if (s > THRESHOLD_SILENCE) {
                    bSilence = false
                }
            }
            if (bSilence) { // 静寂
                dataCount = 0
                continue
            }
            var copyLength = 0
            // データを mTestBuf へ順次アペンド
            if (dataCount < mTestBuf!!.size) {
                // mTestBuf の残領域に応じてコピーするサイズを決定
                val remain = mTestBuf!!.size - dataCount
                if (remain > mBufferSizeInShort) {
                    copyLength = mBufferSizeInShort
                } else {
                    copyLength = remain
                }
                System.arraycopy(mRecordBuf!!, 0, mTestBuf!!, dataCount, copyLength)
                dataCount += copyLength
            }
            if (dataCount >= mTestBuf!!.size) {
                // 100ms 分溜まったら FFT にかける
                var freq = doFFT(mTestBuf!!)
                // 待ってた範囲の周波数かチェック
                if (freq >= FREQ_BASE && freq <= FREQ_MAX) {
                    val `val` = (freq - FREQ_BASE) / FREQ_STEP
                    if (`val` >= 0 && `val` <= 255) {
                        val msg = Message()
                        msg.what = MSG_DATA_RECV
                        msg.arg1 = `val`
                        mHandler!!.sendMessage(msg)
                    } else {
                        freq = -1
                    }
                } else {
                    freq = -1
                }
                dataCount = 0
                if (freq == -1) {
                    continue
                }
                // mRecordBuf の途中までを mTestBuf へコピーして FFT した場合は
                // mRecordBuf の残データを mTestBuf 先頭へコピーした上で継続
                if (copyLength < mBufferSizeInShort) {
                    val startPos = copyLength
                    copyLength = mBufferSizeInShort - copyLength
                    System.arraycopy(mRecordBuf!!, startPos, mTestBuf!!, 0, copyLength)
                    dataCount += copyLength
                }
            }
        }
        // 集音終了
        mAudioRecord!!.stop()
        mHandler!!.sendEmptyMessage(MSG_RECORD_END)
    }

    private fun doFFT(data: ShortArray): Int {
        for (i in 0 until mFFTSize) {
            mFFTBuffer!![i] = data[i].toDouble()
        }
        // FFT 実行
        mFFT!!.realForward(mFFTBuffer)

        // 処理結果の複素数配列からピーク周波数成分の要素番号を得る
        var maxAmp = 0.0
        var index = 0
        for (i in 0 until mFFTSize / 2) {
            val a = mFFTBuffer!![i * 2] // 実部
            val b = mFFTBuffer!![i * 2 + 1] // 虚部
            // a+ib の絶対値 √ a^2 + b^2 = r が振幅値
            val r = Math.sqrt(a * a + b * b)
            if (r > maxAmp) {
                maxAmp = r
                index = i
            }
        }
        return index * SAMPLE_RATE / mFFTSize
    }

    companion object {

        private val SAMPLE_RATE = 44100
        private val THRESHOLD_SILENCE: Short = 0x00ff
        private val FREQ_BASE = 16000
        private val FREQ_STEP = 20
        private val FREQ_MAX = FREQ_BASE + 255 * FREQ_STEP
        private val UNITSIZE = SAMPLE_RATE / 10 // 100msec分

        private val MSG_RECORD_START = 100
        private val MSG_RECORD_END = 110
        private val MSG_DATA_RECV = 120
    }
}


