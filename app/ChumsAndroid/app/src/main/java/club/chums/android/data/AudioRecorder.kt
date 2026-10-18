package club.chums.android.data

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AudioRecorder(private val context: Context) {
    private val tag = "AudioRecorder"
    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT

    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var recordThread: Thread? = null
    private val pcmBuffer = ByteArrayOutputStream()

    @SuppressLint("MissingPermission")
    fun startRecording(): Boolean {
        if (isRecording) return true

        val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        if (minBufferSize == AudioRecord.ERROR || minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
            Log.e(tag, "Bad audio buffer size")
            return false
        }

        val bufferSize = minBufferSize.coerceAtLeast(sampleRate * 2) // at least 1s buffer
        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(tag, "AudioRecord failed to initialize")
                return false
            }

            pcmBuffer.reset()
            audioRecord?.startRecording()
            isRecording = true

            recordThread = Thread {
                val readBuffer = ByteArray(1024)
                while (isRecording) {
                    val read = audioRecord?.read(readBuffer, 0, readBuffer.size) ?: -1
                    if (read > 0) {
                        synchronized(pcmBuffer) {
                            pcmBuffer.write(readBuffer, 0, read)
                        }
                    }
                }
            }.apply { start() }

            return true
        } catch (e: Exception) {
            Log.e(tag, "Error starting recording", e)
            return false
        }
    }

    suspend fun stopRecording(): File? = withContext(Dispatchers.IO) {
        if (!isRecording) return@withContext null

        isRecording = false
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Log.e(tag, "Error stopping recorder", e)
        }
        audioRecord = null
        recordThread?.join(1000)
        recordThread = null

        val rawPcm = synchronized(pcmBuffer) { pcmBuffer.toByteArray() }
        if (rawPcm.isEmpty()) return@withContext null

        // Chums design rule: pad short clips with silence because Whisper drops audio < 1.0s
        val minSamples = sampleRate * 2 // 1 second of 16-bit mono = 32000 bytes
        val finalPcm = if (rawPcm.size < minSamples) {
            val padBytes = minSamples - rawPcm.size
            val frontPad = (padBytes / 2) and -2 // ensure 16-bit alignment
            val backPad = (padBytes - frontPad) and -2

            ByteArray(frontPad + rawPcm.size + backPad).apply {
                System.arraycopy(rawPcm, 0, this, frontPad, rawPcm.size)
            }
        } else {
            rawPcm
        }

        val outputFile = File(context.cacheDir, "utterance_${System.currentTimeMillis()}.wav")
        writeWavFile(outputFile, finalPcm, sampleRate)
        outputFile
    }

    private fun writeWavFile(file: File, pcmData: ByteArray, sampleRate: Int) {
        FileOutputStream(file).use { out ->
            val totalAudioLen = pcmData.size
            val totalDataLen = totalAudioLen + 36
            val byteRate = sampleRate * 2 // 16-bit mono

            val header = ByteBuffer.allocate(44).apply {
                order(ByteOrder.LITTLE_ENDIAN)
                put("RIFF".toByteArray())
                putInt(totalDataLen)
                put("WAVE".toByteArray())
                put("fmt ".toByteArray())
                putInt(16) // SubChunk1Size (16 for PCM)
                putShort(1) // AudioFormat (1 for PCM)
                putShort(1) // NumChannels (1 for mono)
                putInt(sampleRate)
                putInt(byteRate)
                putShort(2) // BlockAlign
                putShort(16) // BitsPerSample
                put("data".toByteArray())
                putInt(totalAudioLen)
            }.array()

            out.write(header)
            out.write(pcmData)
        }
    }
}
