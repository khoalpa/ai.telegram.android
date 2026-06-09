package ai.telegram.android.data.telegram

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.util.Log
import androidx.core.content.ContextCompat
import io.github.pytgcalls.NTgCalls
import io.github.pytgcalls.media.AudioDescription
import io.github.pytgcalls.media.MediaDescription
import io.github.pytgcalls.media.MediaSource
import io.github.pytgcalls.media.StreamMode
import io.github.pytgcalls.media.VideoDescription
import io.github.pytgcalls.p2p.RTCServer
import org.json.JSONObject
import org.webrtc.Camera1Enumerator
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraEnumerator

class TelegramCallMediaEngine(
    private val context: Context,
    private val onSignalingData: (TelegramCallSignalingData) -> Unit,
    private val onError: (String) -> Unit = {}
) {
    private companion object {
        const val TAG = "TelegramCallMedia"
        const val TELEGRAM_LEGACY_CALL_MIN_LAYER = 65
        val TELEGRAM_PRIVATE_CALL_LIBRARY_VERSIONS = listOf(
            "2.4.4",
            "2.7.7",
            "5.0.0",
            "7.0.0",
            "8.0.0",
            "9.0.0",
            "12.0.0",
            "13.0.0"
        )
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val engine = runCatching { NTgCalls() }.getOrNull()
    private val activeCalls = mutableSetOf<Int>()
    private var previousAudioMode: Int? = null
    private var previousSpeakerphoneState: Boolean? = null
    private var previousMicrophoneMuteState: Boolean? = null

    val isAvailable: Boolean = engine != null && runCatching {
        NTgCalls.ping()
        NTgCalls.getProtocol()
        true
    }.getOrDefault(false)

    init {
        engine?.setSignalingDataCallback { callId, data ->
            Log.d(TAG, "signalingData callId=$callId bytes=${data.size}")
            onSignalingData(TelegramCallSignalingData(callId.toInt(), data))
        }
        engine?.setConnectionChangeCallback { callId, info ->
            Log.d(TAG, "connection callId=$callId kind=${info.kind} state=${info.state}")
        }
    }

    fun protocol(): TelegramCallProtocol {
        return runCatching {
            val protocol = NTgCalls.getProtocol()
            TelegramCallProtocol(
                minLayer = minOf(protocol.minLayer, TELEGRAM_LEGACY_CALL_MIN_LAYER),
                maxLayer = protocol.maxLayer,
                udpP2p = protocol.udpP2P,
                udpReflector = protocol.udpReflector,
                libraryVersions = (protocol.libraryVersions + TELEGRAM_PRIVATE_CALL_LIBRARY_VERSIONS).distinct()
            )
        }.getOrDefault(TelegramCallProtocol())
    }

    @Synchronized
    fun handleCallUpdate(call: TelegramCall) {
        when (call.state) {
            TelegramCallState.Ready -> {
                Log.d(TAG, "ready callId=${call.id} outgoing=${call.isOutgoing} video=${call.isVideo}")
                connect(call)
            }
            TelegramCallState.Discarded,
            TelegramCallState.Error -> stop(call.id)
            TelegramCallState.Pending,
            TelegramCallState.ExchangingKeys,
            TelegramCallState.HangingUp,
            TelegramCallState.Unknown -> Unit
        }
    }

    @Synchronized
    fun handleSignalingData(signalingData: TelegramCallSignalingData) {
        if (!activeCalls.contains(signalingData.callId)) return
        val ntgCalls = engine ?: return
        runCatching {
            ntgCalls.sendSignalingData(signalingData.callId.toLong(), signalingData.data)
        }.onFailure { error ->
            onError("Media signaling failed: ${error.message ?: error::class.java.simpleName}")
        }
    }

    @Synchronized
    fun stop(callId: Int) {
        if (!activeCalls.remove(callId)) return
        engine?.let { ntgCalls ->
            runCatching { ntgCalls.stop(callId.toLong()) }
        }
        if (activeCalls.isEmpty()) restoreAudioRoute()
    }

    @Synchronized
    fun close() {
        activeCalls.toList().forEach(::stop)
    }

    @SuppressLint("MissingPermission")
    private fun connect(call: TelegramCall) {
        if (activeCalls.contains(call.id)) return
        val ntgCalls = engine ?: run {
            onError("Media engine is unavailable on this device.")
            return
        }
        if (call.encryptionKey.isEmpty() || call.servers.isEmpty()) {
            onError("Call media parameters are incomplete.")
            return
        }
        if (!hasCapturePermissions(video = call.isVideo)) {
            onError(
                if (call.isVideo) {
                    "Microphone and camera permissions are required to start this video call."
                } else {
                    "Microphone permission is required to start this voice call."
                }
            )
            return
        }

        runCatching {
            prepareAudioRoute(video = call.isVideo)
            val callId = call.id.toLong()
            ntgCalls.createP2PCall(callId)
            Log.d(TAG, "created P2P callId=$callId")
            ntgCalls.setStreamSources(callId, StreamMode.CAPTURE, mediaDescription(video = call.isVideo))
            Log.d(TAG, "stream sources set callId=$callId")
            ntgCalls.skipExchange(callId, call.encryptionKey, call.isOutgoing)
            Log.d(TAG, "exchange skipped callId=$callId outgoing=${call.isOutgoing}")
            ntgCalls.connectP2P(
                callId,
                call.servers.map { it.toRtcServer() },
                call.protocol.libraryVersions.takeIf { it.isNotEmpty() } ?: protocol().libraryVersions,
                call.allowP2p
            )
            activeCalls += call.id
            Log.d(TAG, "connectP2P requested callId=$callId servers=${call.servers.size}")
        }.onFailure { error ->
            restoreAudioRoute()
            Log.e(TAG, "connect failed callId=${call.id}", error)
            onError("Could not start call media: ${error.message ?: error::class.java.simpleName}")
        }
    }

    private fun mediaDescription(video: Boolean): MediaDescription {
        val microphone = AudioDescription(MediaSource.DEVICE, "", true, 48_000, 1)
        val speaker = AudioDescription(MediaSource.DEVICE, "", true, 48_000, 1)
        val camera = if (video) {
            val cameraMetadata = preferredCameraMetadata()
                ?: throw IllegalStateException("No camera device is available for this video call.")
            VideoDescription(MediaSource.DEVICE, cameraMetadata, true, 1280, 720, 30)
        } else {
            null
        }
        return MediaDescription(microphone, speaker, camera, null)
    }

    private fun preferredCameraMetadata(): String? {
        val enumerator = cameraEnumeratorOrNull() ?: return null
        val deviceNames = runCatching { enumerator.deviceNames.toList() }.getOrDefault(emptyList())
        val deviceName = deviceNames.firstOrNull { name ->
            runCatching { enumerator.isFrontFacing(name) }.getOrDefault(false)
        } ?: deviceNames.firstOrNull()
        if (deviceName.isNullOrBlank()) return null
        val metadata = JSONObject()
            .put("id", deviceName)
            .put("is_front", runCatching { enumerator.isFrontFacing(deviceName) }.getOrDefault(false))
            .toString()
        Log.d(TAG, "camera metadata selected name=$deviceName")
        return metadata
    }

    private fun cameraEnumeratorOrNull(): CameraEnumerator? {
        return runCatching {
            if (Camera2Enumerator.isSupported(context)) {
                Camera2Enumerator(context)
            } else {
                Camera1Enumerator()
            }
        }.getOrNull()
    }

    private fun hasCapturePermissions(video: Boolean): Boolean {
        val hasMicrophone = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        val hasCamera = !video || ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        return hasMicrophone && hasCamera
    }

    private fun TelegramCallServer.toRtcServer(): RTCServer {
        return RTCServer(
            id,
            ipv4,
            ipv6,
            port,
            username,
            password,
            supportsTurn,
            supportsStun,
            isTcp,
            peerTag
        )
    }

    private fun prepareAudioRoute(video: Boolean) {
        val manager = audioManager ?: return
        if (previousAudioMode == null) {
            previousAudioMode = manager.mode
            previousSpeakerphoneState = manager.speakerphoneOnCompat()
            previousMicrophoneMuteState = manager.isMicrophoneMute
        }
        manager.mode = AudioManager.MODE_IN_COMMUNICATION
        manager.isMicrophoneMute = false
        manager.setSpeakerphoneOnCompat(video)
    }

    private fun restoreAudioRoute() {
        val manager = audioManager ?: return
        previousMicrophoneMuteState?.let { manager.isMicrophoneMute = it }
        previousSpeakerphoneState?.let { manager.setSpeakerphoneOnCompat(it) }
        previousAudioMode?.let { manager.mode = it }
        previousAudioMode = null
        previousSpeakerphoneState = null
        previousMicrophoneMuteState = null
    }

    @Suppress("DEPRECATION")
    private fun AudioManager.speakerphoneOnCompat(): Boolean {
        return isSpeakerphoneOn
    }

    @Suppress("DEPRECATION")
    private fun AudioManager.setSpeakerphoneOnCompat(enabled: Boolean) {
        isSpeakerphoneOn = enabled
    }
}
