package com.example.fairwaypicnic.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.example.fairwaypicnic.model.AppSettings
import com.example.fairwaypicnic.model.GameCategory
import com.example.fairwaypicnic.model.GolfGameType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

enum class BgmTheme(val title: String) {
    HOME_MEADOW("햇살 페어웨이 산책"),
    TEE_ORDER_BREEZE("설레는 첫 홀의 봄바람"),
    GREEN_TENSION_CAPER("호숫가 그늘집의 내기"),
    WINDMILL_WALTZ("언덕 위 풍차 마을 왈츠")
}

private enum class SoundChannel {
    GAME_SFX,
    HEARTBEAT,
    RESULT_FANFARE
}

data class SystemAudioSnapshot(
    val mediaVolume: Int = 10,
    val mediaMaxVolume: Int = 15,
    val ringerMode: Int = AudioManager.RINGER_MODE_NORMAL,
    val externalDeviceLabel: String? = null
) {
    val mediaFraction: Float
        get() = if (mediaMaxVolume > 0) (mediaVolume.toFloat() / mediaMaxVolume.toFloat()).coerceIn(0f, 1f) else 0f

    val ringerModeLabel: String
        get() = when (ringerMode) {
            AudioManager.RINGER_MODE_SILENT -> "휴대폰 무음 모드 (자동 음소거)"
            AudioManager.RINGER_MODE_VIBRATE -> "휴대폰 진동 모드"
            else -> "휴대폰 소리 모드"
        }
}

private data class ScoreNote(
    val startBeat: Double,
    val durationBeats: Double,
    val midi: Int,
    val velocity: Double,
    val instrument: Int // 0: Felt Piano, 1: Ocarina/Flute, 2: Pizzicato/Bass, 3: Celesta/Marimba
)

/**
 * 지브리 감성 배경음악(BGM) 4종과 게임 시작·진행·클라이맥스·결과 효과음을 실시간 합성 및 재생하고
 * 휴대폰 하드웨어 미디어 볼륨(내장·외장·블루투스)과 무음 모드를 즉각 반영하는 오디오 엔진
 */
class GhibliSoundEngine(context: Context) {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val bgmMutex = Mutex()

    private val sfxSampleRate = 44100
    private val bgmSampleRate = 24000

    // 고속 파형 합성을 위한 4096 포인트 사인 룩업 테이블
    private val sineTable = FloatArray(4096) { idx ->
        sin(2.0 * PI * idx / 4096.0).toFloat()
    }

    @Volatile
    var soundEnabled: Boolean = true
        private set

    @Volatile
    var bgmEnabled: Boolean = true
        private set

    @Volatile
    var bgmVolume: Float = 0.65f
        private set

    @Volatile
    var sfxEnabled: Boolean = true
        private set

    @Volatile
    var sfxVolume: Float = 0.88f
        private set

    @Volatile
    var heartbeatEnabled: Boolean = true
        private set

    @Volatile
    var heartbeatVolume: Float = 0.92f
        private set

    @Volatile
    var resultFanfareEnabled: Boolean = true
        private set

    @Volatile
    var resultFanfareVolume: Float = 0.94f
        private set

    @Volatile
    var muteInVibrateMode: Boolean = false
        private set

    @Volatile
    var hapticEnabled: Boolean = true
        private set

    @Volatile
    private var isAppInForeground: Boolean = true

    @Volatile
    private var isClimaxDucked: Boolean = false

    @Volatile
    private var desiredBgmTheme: BgmTheme = BgmTheme.HOME_MEADOW

    private var activeBgmTheme: BgmTheme? = null
    private var activeBgmTrack: AudioTrack? = null

    private val _systemAudioState = MutableStateFlow(readSystemAudioSnapshot())
    val systemAudioState: StateFlow<SystemAudioSnapshot> = _systemAudioState.asStateFlow()

    // 사전 합성된 효과음 PCM 캐시
    private var gameStartOrderPcm: ShortArray? = null
    private var gameStartPenaltyPcm: ShortArray? = null
    private var teeTossStartPcm: ShortArray? = null
    private var teeBouncePcm: ShortArray? = null
    private var spinWhooshLowPcm: ShortArray? = null
    private var spinWhooshHighPcm: ShortArray? = null
    private var grassLandPcm: ShortArray? = null
    private var stickCaseShufflePcm: ShortArray? = null
    private val stickSlideNotes = arrayOfNulls<ShortArray>(6)
    private val notchRevealNotes = arrayOfNulls<ShortArray>(6)
    private var ladderWhistlePcm: ShortArray? = null
    private var woodTickLowPcm: ShortArray? = null
    private var woodTickHighPcm: ShortArray? = null
    private var puttStrokePcm: ShortArray? = null
    private var ballRollTickPcm: ShortArray? = null
    private var waterSplashPcm: ShortArray? = null
    private var cupInPcm: ShortArray? = null
    private var basketShufflePcm: ShortArray? = null
    private var ballInspectPopPcm: ShortArray? = null
    private var windmillStartPcm: ShortArray? = null
    private var roulettePegLowPcm: ShortArray? = null
    private var roulettePegHighPcm: ShortArray? = null
    private var tileShufflePcm: ShortArray? = null
    private var cardFlipPcm: ShortArray? = null
    private var tileTurnWhooshPcm: ShortArray? = null
    private var heartbeatNormalPcm: ShortArray? = null
    private var heartbeatIntensePcm: ShortArray? = null
    private var slowMoEnterPcm: ShortArray? = null
    private var ghibliFanfarePcm: ShortArray? = null
    private var penaltyRevealPcm: ShortArray? = null

    // 사전 합성된 지브리 배경음악 4종 루프 캐시
    private val bgmPcmMap = java.util.concurrent.ConcurrentHashMap<BgmTheme, ShortArray>()

    private val audioSystemReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refreshSystemAudioState()
        }
    }

    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            refreshSystemAudioState()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            refreshSystemAudioState()
        }
    }

    private var receiverRegistered = false

    init {
        registerSystemAudioObservers()

        // 1차: 즉각 반응해야 하는 효과음 고속 합성 후 로비 배경음악 즉시 시작
        scope.launch {
            heartbeatNormalPcm = synthHeartbeat(intensity = 0.75f)
            heartbeatIntensePcm = synthHeartbeat(intensity = 1.0f)
            slowMoEnterPcm = synthSlowMoShimmer()
            gameStartOrderPcm = synthGameCurtainStart(brightMajor = true)
            gameStartPenaltyPcm = synthGameCurtainStart(brightMajor = false)
            teeTossStartPcm = synthAiryWhoosh(duration = 0.20, startFreq = 360.0, endFreq = 780.0)
            teeBouncePcm = synthTitaniumTeeBounce()
            spinWhooshLowPcm = synthBambooSpinTick(freq = 540.0)
            spinWhooshHighPcm = synthBambooSpinTick(freq = 680.0)
            grassLandPcm = synthSoftGrassDrop()
            stickCaseShufflePcm = synthWoodenRattleSequence(count = 5, baseFreq = 490.0)

            val pentatonic = listOf(523.25, 587.33, 659.25, 783.99, 880.00, 1046.50)
            pentatonic.forEachIndexed { idx, f ->
                stickSlideNotes[idx] = synthMarimbaNote(f, 0.28)
            }
            val celestaScale = listOf(1046.50, 1174.66, 1318.51, 1567.98, 1760.00, 2093.00)
            celestaScale.forEachIndexed { idx, f ->
                notchRevealNotes[idx] = synthCelestaBell(f, 0.34)
            }

            ladderWhistlePcm = synthOcarinaStartWhistle()
            woodTickLowPcm = synthWoodBlock(freq = 620.0)
            woodTickHighPcm = synthWoodBlock(freq = 840.0)
            puttStrokePcm = synthPuttImpact()
            ballRollTickPcm = synthGrassRollTick()
            waterSplashPcm = synthWaterSplash()
            cupInPcm = synthGolfCupIn()
            basketShufflePcm = synthWoodenRattleSequence(count = 4, baseFreq = 610.0)
            ballInspectPopPcm = synthHollowBallPop()
            windmillStartPcm = synthAiryWhoosh(duration = 0.32, startFreq = 260.0, endFreq = 620.0)
            roulettePegLowPcm = synthBrassPegClick(freq = 760.0)
            roulettePegHighPcm = synthBrassPegClick(freq = 960.0)
            tileShufflePcm = synthWoodenRattleSequence(count = 6, baseFreq = 540.0)
            cardFlipPcm = synthCardFlip()
            tileTurnWhooshPcm = synthAiryWhoosh(duration = 0.28, startFreq = 310.0, endFreq = 690.0)
            ghibliFanfarePcm = synthGhibliChordFanfare()
            penaltyRevealPcm = synthPlayfulPenaltyTune()

            // 로비 테마 우선 합성 및 재생 시작
            bgmPcmMap[BgmTheme.HOME_MEADOW] = synthBgmTheme(BgmTheme.HOME_MEADOW)
            ensureBgmPlayback()

            // 나머지 게임 테마 백그라운드 순차 합성
            BgmTheme.entries.filter { it != BgmTheme.HOME_MEADOW }.forEach { theme ->
                bgmPcmMap[theme] = synthBgmTheme(theme)
                if (desiredBgmTheme == theme) {
                    ensureBgmPlayback()
                }
            }
        }
    }

    fun applySettings(settings: AppSettings) {
        soundEnabled = settings.soundEnabled
        bgmEnabled = settings.bgmEnabled
        bgmVolume = settings.bgmVolume.coerceIn(0f, 1f)
        sfxEnabled = settings.sfxEnabled
        sfxVolume = settings.sfxVolume.coerceIn(0f, 1f)
        heartbeatEnabled = settings.heartbeatEnabled
        heartbeatVolume = settings.heartbeatVolume.coerceIn(0f, 1f)
        resultFanfareEnabled = settings.resultFanfareEnabled
        resultFanfareVolume = settings.resultFanfareVolume.coerceIn(0f, 1f)
        muteInVibrateMode = settings.muteInVibrateMode
        hapticEnabled = settings.hapticEnabled

        scope.launch {
            ensureBgmPlayback()
        }
    }

    fun setPhoneMediaVolumeFraction(fraction: Float) {
        try {
            val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
            val target = (fraction.coerceIn(0f, 1f) * max).roundToInt().coerceIn(0, max)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
            refreshSystemAudioState()
        } catch (_: Throwable) {
        }
    }

    fun refreshSystemAudioState() {
        val snap = readSystemAudioSnapshot()
        _systemAudioState.value = snap
        scope.launch {
            updateActiveBgmTrackGain()
        }
    }

    fun setBgmForScreen(gameType: GolfGameType?) {
        val nextTheme = when (gameType) {
            null -> BgmTheme.HOME_MEADOW
            GolfGameType.TEE_BOUNCE,
            GolfGameType.NOTCH_STICK,
            GolfGameType.FOREST_LADDER -> BgmTheme.TEE_ORDER_BREEZE
            GolfGameType.NEAR_PIN_PUTTING,
            GolfGameType.BALL_BASKET -> BgmTheme.GREEN_TENSION_CAPER
            GolfGameType.WINDMILL_ROULETTE,
            GolfGameType.LUCKY_WOOD_TILE -> BgmTheme.WINDMILL_WALTZ
        }
        desiredBgmTheme = nextTheme
        isClimaxDucked = false
        scope.launch {
            ensureBgmPlayback()
        }
    }

    fun setClimaxDucking(ducked: Boolean) {
        if (isClimaxDucked == ducked) return
        isClimaxDucked = ducked
        scope.launch {
            updateActiveBgmTrackGain()
        }
    }

    fun onAppForeground() {
        isAppInForeground = true
        refreshSystemAudioState()
        scope.launch {
            ensureBgmPlayback()
        }
    }

    fun onAppBackground() {
        isAppInForeground = false
        scope.launch {
            bgmMutex.withLock {
                try {
                    if (activeBgmTrack?.playState == AudioTrack.PLAYSTATE_PLAYING) {
                        activeBgmTrack?.pause()
                    }
                } catch (_: Throwable) {
                }
            }
        }
    }

    private fun isMutedByPhoneOrSettings(): Boolean {
        if (!soundEnabled) return true
        val snap = _systemAudioState.value
        if (snap.ringerMode == AudioManager.RINGER_MODE_SILENT) return true
        if (muteInVibrateMode && snap.ringerMode == AudioManager.RINGER_MODE_VIBRATE) return true
        if (snap.mediaVolume <= 0) return true
        return false
    }

    private fun computeEffectiveBgmGain(): Float {
        if (!isAppInForeground || !bgmEnabled || isMutedByPhoneOrSettings()) return 0f
        val duckMultiplier = if (isClimaxDucked) 0.18f else 1.0f
        return (bgmVolume * 0.58f * duckMultiplier).coerceIn(0f, 1f)
    }

    private suspend fun updateActiveBgmTrackGain() {
        bgmMutex.withLock {
            val track = activeBgmTrack ?: return@withLock
            val gain = computeEffectiveBgmGain()
            try {
                track.setVolume(gain)
                if (gain <= 0.0001f) {
                    if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                        track.pause()
                    }
                } else if (isAppInForeground && track.playState != AudioTrack.PLAYSTATE_PLAYING) {
                    track.play()
                }
            } catch (_: Throwable) {
            }
        }
    }

    private suspend fun ensureBgmPlayback() {
        val target = desiredBgmTheme
        val pcm = bgmPcmMap[target] ?: return

        bgmMutex.withLock {
            val gain = computeEffectiveBgmGain()
            if (activeBgmTheme == target && activeBgmTrack != null) {
                try {
                    activeBgmTrack?.setVolume(gain)
                    if (gain <= 0.0001f || !isAppInForeground) {
                        if (activeBgmTrack?.playState == AudioTrack.PLAYSTATE_PLAYING) {
                            activeBgmTrack?.pause()
                        }
                    } else if (activeBgmTrack?.playState != AudioTrack.PLAYSTATE_PLAYING) {
                        activeBgmTrack?.play()
                    }
                    return@withLock
                } catch (_: Throwable) {
                }
            }

            // 기존 트랙 안전 해제 후 새 테마 루프 트랙 시작
            releaseBgmTrackLocked()
            if (!isAppInForeground) return@withLock

            try {
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(bgmSampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes((pcm.size * 2).coerceAtLeast(4096))
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                track.write(pcm, 0, pcm.size)
                track.setLoopPoints(0, pcm.size, -1)
                track.setVolume(gain)
                if (gain > 0.0001f) {
                    track.play()
                }
                activeBgmTrack = track
                activeBgmTheme = target
            } catch (_: Throwable) {
                activeBgmTrack = null
                activeBgmTheme = null
            }
        }
    }

    private fun releaseBgmTrackLocked() {
        val old = activeBgmTrack
        activeBgmTrack = null
        activeBgmTheme = null
        if (old != null) {
            try {
                old.stop()
            } catch (_: Throwable) {
            }
            try {
                old.release()
            } catch (_: Throwable) {
            }
        }
    }

    // --- 게임 시작 · 진행 · 클라이맥스 · 결과 효과음 호출 API ---

    fun playGameStart(gameType: GolfGameType) {
        if (hapticEnabled) vibrateSingle(24, 150)
        val pcm = if (gameType.category == GameCategory.TEE_ORDER) gameStartOrderPcm else gameStartPenaltyPcm
        playBuffer(pcm, SoundChannel.GAME_SFX, baseVolume = 0.88f)
    }

    fun playTeeTossStart() {
        if (hapticEnabled) vibrateSingle(16, 120)
        playBuffer(teeTossStartPcm, SoundChannel.GAME_SFX, baseVolume = 0.80f)
    }

    fun playTeeBounce() {
        if (hapticEnabled) vibrateSingle(24, 190)
        playBuffer(teeBouncePcm, SoundChannel.GAME_SFX, baseVolume = 0.90f)
    }

    fun playSpinWhooshTick(highPitch: Boolean = false) {
        if (hapticEnabled) vibrateSingle(8, 65)
        playBuffer(
            if (highPitch) spinWhooshHighPcm else spinWhooshLowPcm,
            SoundChannel.GAME_SFX,
            baseVolume = 0.65f
        )
    }

    fun playGrassLand() {
        if (hapticEnabled) vibrateSingle(14, 90)
        playBuffer(grassLandPcm, SoundChannel.GAME_SFX, baseVolume = 0.70f)
    }

    fun playStickCaseShuffle() {
        if (hapticEnabled) vibratePattern(longArrayOf(0, 18, 45, 18, 45, 22), intArrayOf(0, 120, 0, 150, 0, 130))
        playBuffer(stickCaseShufflePcm, SoundChannel.GAME_SFX, baseVolume = 0.82f)
    }

    fun playStickSlide(orderIndex: Int) {
        if (hapticEnabled) vibrateSingle(18, 130)
        val note = stickSlideNotes[orderIndex.coerceIn(0, stickSlideNotes.lastIndex)]
        playBuffer(note, SoundChannel.GAME_SFX, baseVolume = 0.85f)
    }

    fun playNotchRevealTick(stepIndex: Int) {
        if (hapticEnabled) vibrateSingle(14, 110)
        val bell = notchRevealNotes[stepIndex.coerceIn(0, notchRevealNotes.lastIndex)]
        playBuffer(bell, SoundChannel.GAME_SFX, baseVolume = 0.84f)
    }

    fun playLadderStartWhistle() {
        if (hapticEnabled) vibrateSingle(22, 145)
        playBuffer(ladderWhistlePcm, SoundChannel.GAME_SFX, baseVolume = 0.85f)
    }

    fun playWoodTick(highPitch: Boolean = false) {
        if (hapticEnabled) vibrateSingle(10, 80)
        playBuffer(
            if (highPitch) woodTickHighPcm else woodTickLowPcm,
            SoundChannel.GAME_SFX,
            baseVolume = 0.72f
        )
    }

    fun playPuttStroke() {
        if (hapticEnabled) vibrateSingle(22, 175)
        playBuffer(puttStrokePcm, SoundChannel.GAME_SFX, baseVolume = 0.88f)
    }

    fun playBallRollTick() {
        playBuffer(ballRollTickPcm, SoundChannel.GAME_SFX, baseVolume = 0.48f)
    }

    fun playWaterSplash() {
        if (hapticEnabled) vibratePattern(longArrayOf(0, 35, 45, 55), intArrayOf(0, 200, 0, 140))
        playBuffer(waterSplashPcm, SoundChannel.GAME_SFX, baseVolume = 0.90f)
    }

    fun playCupIn() {
        if (hapticEnabled) vibratePattern(longArrayOf(0, 20, 40, 30), intArrayOf(0, 180, 0, 220))
        playBuffer(cupInPcm, SoundChannel.GAME_SFX, baseVolume = 0.92f)
    }

    fun playBasketShuffle() {
        if (hapticEnabled) vibrateSingle(20, 120)
        playBuffer(basketShufflePcm, SoundChannel.GAME_SFX, baseVolume = 0.80f)
    }

    fun playBallInspectPop() {
        if (hapticEnabled) vibrateSingle(15, 115)
        playBuffer(ballInspectPopPcm, SoundChannel.GAME_SFX, baseVolume = 0.82f)
    }

    fun playWindmillStart() {
        if (hapticEnabled) vibrateSingle(22, 145)
        playBuffer(windmillStartPcm, SoundChannel.GAME_SFX, baseVolume = 0.84f)
    }

    fun playRoulettePegTick(highPitch: Boolean = false) {
        if (hapticEnabled) vibrateSingle(9, 85)
        playBuffer(
            if (highPitch) roulettePegHighPcm else roulettePegLowPcm,
            SoundChannel.GAME_SFX,
            baseVolume = 0.76f
        )
    }

    fun playTileShuffle() {
        if (hapticEnabled) vibratePattern(longArrayOf(0, 15, 40, 15, 40, 18), intArrayOf(0, 115, 0, 140, 0, 120))
        playBuffer(tileShufflePcm, SoundChannel.GAME_SFX, baseVolume = 0.82f)
    }

    fun playCardFlip() {
        if (hapticEnabled) vibrateSingle(15, 110)
        playBuffer(cardFlipPcm, SoundChannel.GAME_SFX, baseVolume = 0.78f)
    }

    fun playTileTurnWhoosh() {
        if (hapticEnabled) vibrateSingle(18, 125)
        playBuffer(tileTurnWhooshPcm, SoundChannel.GAME_SFX, baseVolume = 0.82f)
    }

    fun playHeartbeat(intense: Boolean = false) {
        if (hapticEnabled) {
            vibratePattern(
                if (intense) longArrayOf(0, 42, 95, 65) else longArrayOf(0, 28, 110, 42),
                if (intense) intArrayOf(0, 195, 0, 255) else intArrayOf(0, 125, 0, 180)
            )
        }
        val pcm = if (intense) heartbeatIntensePcm else heartbeatNormalPcm
        playBuffer(pcm, SoundChannel.HEARTBEAT, baseVolume = if (intense) 1.0f else 0.80f)
    }

    fun playSlowMoEnter() {
        if (hapticEnabled) vibrateSingle(45, 165)
        playBuffer(slowMoEnterPcm, SoundChannel.HEARTBEAT, baseVolume = 0.88f)
    }

    fun playGhibliFanfare() {
        if (hapticEnabled) {
            vibratePattern(longArrayOf(0, 25, 60, 30, 60, 65), intArrayOf(0, 150, 0, 190, 0, 245))
        }
        playBuffer(ghibliFanfarePcm, SoundChannel.RESULT_FANFARE, baseVolume = 0.96f)
    }

    fun playPenaltyReveal() {
        if (hapticEnabled) {
            vibratePattern(longArrayOf(0, 40, 70, 40, 70, 95), intArrayOf(0, 185, 0, 200, 0, 255))
        }
        playBuffer(penaltyRevealPcm, SoundChannel.RESULT_FANFARE, baseVolume = 0.96f)
    }

    fun release() {
        unregisterSystemAudioObservers()
        val old = activeBgmTrack
        activeBgmTrack = null
        if (old != null) {
            try {
                old.stop()
            } catch (_: Throwable) {
            }
            try {
                old.release()
            } catch (_: Throwable) {
            }
        }
        scope.cancel()
    }

    private fun playBuffer(pcm: ShortArray?, channel: SoundChannel, baseVolume: Float) {
        if (pcm == null || pcm.isEmpty() || isMutedByPhoneOrSettings()) return

        val channelGain = when (channel) {
            SoundChannel.GAME_SFX -> if (sfxEnabled) sfxVolume else 0f
            SoundChannel.HEARTBEAT -> if (heartbeatEnabled) heartbeatVolume else 0f
            SoundChannel.RESULT_FANFARE -> if (resultFanfareEnabled) resultFanfareVolume else 0f
        }
        val finalVol = (baseVolume * channelGain).coerceIn(0f, 1f)
        if (finalVol <= 0.001f) return

        scope.launch {
            var track: AudioTrack? = null
            try {
                val byteCount = pcm.size * 2
                // USAGE_MEDIA + CONTENT_TYPE_MUSIC 으로 통일하여 내장 스피커·외장 스피커·블루투스 STREAM_MUSIC 볼륨에 100% 종속
                track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sfxSampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(byteCount.coerceAtLeast(2048))
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                track.write(pcm, 0, pcm.size)
                track.setVolume(finalVol)
                track.play()
                val durationMs = (pcm.size * 1000L / sfxSampleRate) + 95L
                delay(durationMs)
            } catch (_: Throwable) {
            } finally {
                try {
                    track?.stop()
                } catch (_: Throwable) {
                }
                try {
                    track?.release()
                } catch (_: Throwable) {
                }
            }
        }
    }

    private fun registerSystemAudioObservers() {
        try {
            val filter = IntentFilter().apply {
                addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
                addAction("android.media.VOLUME_CHANGED_ACTION")
                addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
                addAction(AudioManager.ACTION_HEADSET_PLUG)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                appContext.registerReceiver(audioSystemReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                appContext.registerReceiver(audioSystemReceiver, filter)
            }
            receiverRegistered = true
        } catch (_: Throwable) {
        }

        try {
            audioManager.registerAudioDeviceCallback(audioDeviceCallback, Handler(Looper.getMainLooper()))
        } catch (_: Throwable) {
        }
    }

    private fun unregisterSystemAudioObservers() {
        if (receiverRegistered) {
            try {
                appContext.unregisterReceiver(audioSystemReceiver)
            } catch (_: Throwable) {
            }
            receiverRegistered = false
        }
        try {
            audioManager.unregisterAudioDeviceCallback(audioDeviceCallback)
        } catch (_: Throwable) {
        }
    }

    private fun readSystemAudioSnapshot(): SystemAudioSnapshot {
        return try {
            val vol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
            val ringer = audioManager.ringerMode
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            var extLabel: String? = null
            for (d in devices) {
                when (d.type) {
                    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                    AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                    AudioDeviceInfo.TYPE_BLE_HEADSET,
                    AudioDeviceInfo.TYPE_BLE_SPEAKER -> {
                        extLabel = "블루투스 오디오 출력 연동 중"
                        break
                    }
                    AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                    AudioDeviceInfo.TYPE_WIRED_HEADSET,
                    AudioDeviceInfo.TYPE_USB_HEADSET,
                    AudioDeviceInfo.TYPE_USB_DEVICE -> {
                        extLabel = "외부 스피커·이어폰 출력 연동 중"
                    }
                }
            }
            SystemAudioSnapshot(
                mediaVolume = vol,
                mediaMaxVolume = max,
                ringerMode = ringer,
                externalDeviceLabel = extLabel
            )
        } catch (_: Throwable) {
            SystemAudioSnapshot()
        }
    }

    private fun getVibrator(): Vibrator? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun vibrateSingle(ms: Long, amplitude: Int) {
        scope.launch {
            try {
                val vib = getVibrator() ?: return@launch
                if (!vib.hasVibrator()) return@launch
                vib.vibrate(VibrationEffect.createOneShot(ms, amplitude.coerceIn(1, 255)))
            } catch (_: Throwable) {
            }
        }
    }

    private fun vibratePattern(timings: LongArray, amplitudes: IntArray) {
        scope.launch {
            try {
                val vib = getVibrator() ?: return@launch
                if (!vib.hasVibrator()) return@launch
                vib.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
            } catch (_: Throwable) {
            }
        }
    }

    private fun fastSin(turns: Double): Float {
        val idx = ((turns * 4096.0).toLong() and 4095L).toInt()
        return sineTable[idx]
    }

    private fun midiToHz(midi: Int): Double = 440.0 * 2.0.pow((midi - 69) / 12.0)

    // --- 지브리 수채화 어쿠스틱 배경음악(BGM) 4종 합성기 (무한 무결점 루프 폴딩 적용) ---

    private fun synthBgmTheme(theme: BgmTheme): ShortArray {
        val bpm: Double
        val totalBeats: Double
        val notes = mutableListOf<ScoreNote>()

        when (theme) {
            BgmTheme.HOME_MEADOW -> {
                // "햇살 페어웨이 산책": Fmaj7 - G6 - Em7 - Am7 - Dm9 - G13 - Cmaj9 (16박 루프)
                bpm = 92.0
                totalBeats = 16.0
                val chords = listOf(
                    0.0 to listOf(53, 60, 64, 69),   // Fmaj7
                    4.0 to listOf(55, 62, 64, 71),   // G6
                    8.0 to listOf(52, 59, 62, 67),   // Em7
                    10.0 to listOf(57, 64, 67, 72),  // Am7
                    12.0 to listOf(50, 57, 60, 64),  // Dm9
                    14.0 to listOf(55, 62, 65, 71)   // G13
                )
                chords.forEachIndexed { idx, (bStart, tones) ->
                    val span = if (idx < 2) 4.0 else 2.0
                    notes.add(ScoreNote(bStart, span * 0.95, tones[0] - 12, 0.34, 2))
                    // 부드러운 8분음표 펠트 피아노 아르페지오
                    val steps = (span * 2).toInt()
                    for (s in 0 until steps) {
                        val tone = tones[s % tones.size]
                        notes.add(ScoreNote(bStart + s * 0.5, 0.9, tone, 0.22, 0))
                    }
                }
                // 따뜻한 오카리나·플루트 서정 멜로디
                val melody = listOf(
                    Triple(0.0, 1.5, 76), Triple(1.5, 0.5, 77), Triple(2.0, 1.5, 79), Triple(3.5, 0.5, 81),
                    Triple(4.0, 1.5, 79), Triple(5.5, 0.5, 76), Triple(6.0, 2.0, 74),
                    Triple(8.0, 1.0, 76), Triple(9.0, 1.0, 79), Triple(10.0, 1.5, 81), Triple(11.5, 0.5, 79),
                    Triple(12.0, 1.5, 77), Triple(13.5, 0.5, 76), Triple(14.0, 2.0, 74)
                )
                melody.forEach { (b, d, m) ->
                    notes.add(ScoreNote(b, d * 0.92, m, 0.28, 1))
                    if (b % 4.0 == 0.0) notes.add(ScoreNote(b, 0.8, m + 12, 0.11, 3))
                }
            }

            BgmTheme.TEE_ORDER_BREEZE -> {
                // "설레는 첫 홀의 봄바람": Gmaj7 - Bm7 - Cmaj7 - D9sus4 (경쾌한 산책 피치카토 & 마림바)
                bpm = 108.0
                totalBeats = 16.0
                val prog = listOf(
                    0.0 to listOf(55, 62, 66, 71),  // Gmaj7
                    4.0 to listOf(59, 66, 69, 74),  // Bm7
                    8.0 to listOf(60, 64, 67, 71),  // Cmaj7
                    12.0 to listOf(62, 67, 69, 72)  // D9sus4
                )
                prog.forEach { (bStart, tones) ->
                    notes.add(ScoreNote(bStart, 1.4, tones[0] - 12, 0.36, 2))
                    notes.add(ScoreNote(bStart + 2.0, 1.4, tones[1] - 12, 0.32, 2))
                    for (off in listOf(0.5, 1.5, 2.5, 3.5)) {
                        tones.drop(1).forEach { t ->
                            notes.add(ScoreNote(bStart + off, 0.45, t, 0.15, 0))
                        }
                    }
                }
                val breezeMelody = listOf(
                    Triple(0.0, 1.0, 74), Triple(1.0, 0.5, 76), Triple(1.5, 0.5, 79), Triple(2.0, 1.5, 83), Triple(3.5, 0.5, 81),
                    Triple(4.0, 1.0, 79), Triple(5.0, 1.0, 78), Triple(6.0, 2.0, 74),
                    Triple(8.0, 1.0, 76), Triple(9.0, 0.5, 79), Triple(9.5, 0.5, 81), Triple(10.0, 1.5, 79), Triple(11.5, 0.5, 76),
                    Triple(12.0, 1.0, 78), Triple(13.0, 1.0, 79), Triple(14.0, 2.0, 81)
                )
                breezeMelody.forEach { (b, d, m) ->
                    notes.add(ScoreNote(b, d * 0.85, m, 0.30, 3))
                    notes.add(ScoreNote(b, d * 0.90, m, 0.18, 1))
                }
            }

            BgmTheme.GREEN_TENSION_CAPER -> {
                // "호숫가 그늘집의 내기": Am7 - Dm7 - G7 - Cmaj7 - Fmaj7 - Dm7 - E7sus4 - E7 (호기심 가득한 피치카토 왈츠풍 카프리치오)
                bpm = 114.0
                totalBeats = 16.0
                val bassWalk = listOf(
                    57, 64, 57, 64,  // Am
                    62, 69, 55, 62,  // Dm -> G
                    60, 67, 53, 60,  // C -> F
                    50, 57, 52, 59   // Dm -> E7
                )
                bassWalk.forEachIndexed { beat, m ->
                    notes.add(ScoreNote(beat.toDouble(), 0.55, m - 12, 0.36, 2))
                    notes.add(ScoreNote(beat + 0.5, 0.35, m, 0.18, 0))
                }
                val caperMelody = listOf(
                    Triple(0.0, 0.5, 76), Triple(0.5, 0.5, 77), Triple(1.0, 1.0, 76), Triple(2.0, 1.0, 81), Triple(3.0, 1.0, 79),
                    Triple(4.0, 0.5, 77), Triple(4.5, 0.5, 76), Triple(5.0, 1.0, 74), Triple(6.0, 1.5, 79), Triple(7.5, 0.5, 77),
                    Triple(8.0, 1.0, 76), Triple(9.0, 1.0, 72), Triple(10.0, 1.0, 77), Triple(11.0, 1.0, 76),
                    Triple(12.0, 1.0, 74), Triple(13.0, 1.0, 72), Triple(14.0, 2.0, 76)
                )
                caperMelody.forEach { (b, d, m) ->
                    notes.add(ScoreNote(b, d * 0.78, m, 0.30, 3))
                    notes.add(ScoreNote(b, d * 0.85, m, 0.16, 1))
                }
            }

            BgmTheme.WINDMILL_WALTZ -> {
                // "언덕 위 풍차 마을 왈츠": D Major 3/4박자 유러피언 타운 왈츠 (24박 = 8마디)
                bpm = 136.0
                totalBeats = 24.0
                val waltzBars = listOf(
                    listOf(50, 62, 66, 69), // D
                    listOf(49, 61, 64, 69), // A/C#
                    listOf(47, 59, 62, 66), // Bm
                    listOf(45, 57, 62, 66), // D/A
                    listOf(43, 55, 59, 62), // G
                    listOf(42, 54, 57, 62), // D/F#
                    listOf(40, 55, 59, 64), // Em7
                    listOf(45, 55, 61, 64)  // A7
                )
                waltzBars.forEachIndexed { barIdx, tones ->
                    val b0 = barIdx * 3.0
                    notes.add(ScoreNote(b0, 1.2, tones[0], 0.38, 2))
                    tones.drop(1).forEach { chordTone ->
                        notes.add(ScoreNote(b0 + 1.0, 0.75, chordTone, 0.18, 0))
                        notes.add(ScoreNote(b0 + 2.0, 0.75, chordTone, 0.16, 0))
                    }
                }
                val waltzMelody = listOf(
                    Triple(0.0, 2.0, 78), Triple(2.0, 1.0, 81),
                    Triple(3.0, 2.0, 76), Triple(5.0, 1.0, 78),
                    Triple(6.0, 2.0, 74), Triple(8.0, 1.0, 71),
                    Triple(9.0, 3.0, 74),
                    Triple(12.0, 1.5, 79), Triple(13.5, 0.5, 78), Triple(14.0, 1.0, 76),
                    Triple(15.0, 2.0, 78), Triple(17.0, 1.0, 74),
                    Triple(18.0, 2.0, 76), Triple(20.0, 1.0, 78),
                    Triple(21.0, 3.0, 76)
                )
                waltzMelody.forEach { (b, d, m) ->
                    notes.add(ScoreNote(b, d * 0.92, m, 0.29, 1))
                    if (b % 3.0 == 0.0) notes.add(ScoreNote(b, 0.9, m + 12, 0.10, 3))
                }
            }
        }

        val secPerBeat = 60.0 / bpm
        val loopSeconds = totalBeats * secPerBeat
        val tailSeconds = 1.1
        val loopSamples = (bgmSampleRate * loopSeconds).toInt()
        val tailSamples = (bgmSampleRate * tailSeconds).toInt()
        val mixBuf = FloatArray(loopSamples + tailSamples)

        for (note in notes) {
            val startSec = note.startBeat * secPerBeat
            val durSec = note.durationBeats * secPerBeat
            val freq = midiToHz(note.midi)
            val startSample = (startSec * bgmSampleRate).toInt()
            val renderSeconds = durSec + 0.55
            val endSample = ((startSec + renderSeconds) * bgmSampleRate).toInt().coerceAtMost(mixBuf.size)
            val vel = note.velocity.toFloat()

            var phase = 0.0
            val phaseStep = freq / bgmSampleRate
            for (s in startSample until endSample) {
                val dt = (s - startSample).toDouble() / bgmSampleRate
                val sampleVal: Float = when (note.instrument) {
                    0 -> {
                        // 따뜻한 펠트 피아노
                        val env = ((1.0 - exp(-dt * 95.0)) * exp(-dt * 3.4)).toFloat()
                        val w = fastSin(phase) * 0.74f +
                            fastSin(phase * 2.0) * 0.20f * exp(-dt * 6.0).toFloat() +
                            fastSin(phase * 3.0) * 0.06f * exp(-dt * 10.0).toFloat()
                        w * env * vel
                    }
                    1 -> {
                        // 서정적인 지브리 오카리나·우드 플루트 (은은한 비브라토 포함)
                        val attack = (dt / 0.065).coerceAtMost(1.0)
                        val release = if (dt <= durSec) 1.0 else exp(-(dt - durSec) * 14.0)
                        val vib = 1.0 + 0.0032 * sin(2.0 * PI * 5.2 * dt)
                        val w = fastSin(phase * vib) * 0.82f + fastSin(phase * 2.0 * vib) * 0.14f
                        w * (attack * release).toFloat() * vel
                    }
                    2 -> {
                        // 통통 튀는 어쿠스틱 피치카토 더블베이스
                        val env = ((1.0 - exp(-dt * 160.0)) * exp(-dt * 6.8)).toFloat()
                        val w = fastSin(phase) * 0.78f + fastSin(phase * 2.0) * 0.22f
                        w * env * vel
                    }
                    else -> {
                        // 맑은 첼레스타·마림바 벨
                        val env = ((1.0 - exp(-dt * 180.0)) * exp(-dt * 6.2)).toFloat()
                        val w = fastSin(phase) * 0.72f + fastSin(phase * 3.0) * 0.18f * exp(-dt * 16.0).toFloat()
                        w * env * vel
                    }
                }
                mixBuf[s] += sampleVal
                phase += phaseStep
            }
        }

        // 루프 끝 잔향(Tail)을 루프 시작부로 폴딩하여 0클릭 무결점 루프 보장
        for (i in 0 until tailSamples) {
            if (i < loopSamples) {
                mixBuf[i] += mixBuf[loopSamples + i]
            }
        }

        val out = ShortArray(loopSamples)
        var lp = 0f
        for (i in 0 until loopSamples) {
            // 부드러운 원폴 로우패스로 디지털 거친 배음 제거
            lp = lp * 0.32f + mixBuf[i] * 0.68f
            val softClipped = (lp * 0.78f).coerceIn(-0.92f, 0.92f)
            out[i] = (softClipped * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }

    // --- 개별 효과음 합성기 ---

    private fun synthGameCurtainStart(brightMajor: Boolean): ShortArray {
        val duration = 0.78
        val total = (sfxSampleRate * duration).toInt()
        val out = ShortArray(total)
        val notes = if (brightMajor) {
            listOf(523.25, 587.33, 659.25, 783.99, 1046.50) // C5 D5 E5 G5 C6
        } else {
            listOf(440.00, 523.25, 659.25, 783.99, 880.00)  // A4 C5 E5 G5 A5
        }
        for (i in 0 until total) {
            val t = i.toDouble() / sfxSampleRate
            var acc = 0.0
            notes.forEachIndexed { idx, freq ->
                val onset = idx * 0.068
                if (t >= onset) {
                    val dt = t - onset
                    val env = (1.0 - exp(-dt * 130.0)) * exp(-dt * 4.8)
                    acc += (sin(2.0 * PI * freq * dt) * 0.72 + sin(2.0 * PI * freq * 2.0 * dt) * 0.22) * env * 0.25
                }
            }
            out[i] = (acc.coerceIn(-0.95, 0.95) * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }

    private fun synthAiryWhoosh(duration: Double, startFreq: Double, endFreq: Double): ShortArray {
        val total = (sfxSampleRate * duration).toInt()
        val out = ShortArray(total)
        val rng = Random(2026)
        var lpNoise = 0.0
        var phase = 0.0
        for (i in 0 until total) {
            val t = i.toDouble() / sfxSampleRate
            val u = (t / duration).coerceIn(0.0, 1.0)
            val env = sin(PI * u)
            val freq = startFreq + (endFreq - startFreq) * u
            phase += freq / sfxSampleRate
            lpNoise = lpNoise * 0.86 + rng.nextDouble(-1.0, 1.0) * 0.14
            val tone = sin(2.0 * PI * phase) * 0.35 + lpNoise * 0.65
            out[i] = ((tone * env * 0.78).coerceIn(-0.92, 0.92) * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }

    private fun synthBambooSpinTick(freq: Double): ShortArray {
        val duration = 0.065
        val total = (sfxSampleRate * duration).toInt()
        val out = ShortArray(total)
        for (i in 0 until total) {
            val t = i.toDouble() / sfxSampleRate
            val env = exp(-t * 68.0)
            val wave = sin(2.0 * PI * freq * t) * 0.78 + sin(2.0 * PI * (freq * 1.48) * t) * 0.22
            out[i] = ((wave * env * 0.75).coerceIn(-0.92, 0.92) * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }

    private fun synthWoodenRattleSequence(count: Int, baseFreq: Double): ShortArray {
        val duration = 0.42
        val total = (sfxSampleRate * duration).toInt()
        val out = ShortArray(total)
        for (i in 0 until total) {
            val t = i.toDouble() / sfxSampleRate
            var sum = 0.0
            for (k in 0 until count) {
                val onset = k * 0.068
                if (t >= onset) {
                    val dt = t - onset
                    val f = baseFreq + (k % 3) * 85.0
                    val env = exp(-dt * 55.0)
                    sum += (sin(2.0 * PI * f * dt) * 0.72 + sin(2.0 * PI * f * 1.62 * dt) * 0.28) * env * 0.42
                }
            }
            out[i] = (sum.coerceIn(-0.95, 0.95) * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }

    private fun synthCelestaBell(freq: Double, duration: Double): ShortArray {
        val total = (sfxSampleRate * duration).toInt()
        val out = ShortArray(total)
        for (i in 0 until total) {
            val t = i.toDouble() / sfxSampleRate
            val env = (1.0 - exp(-t * 170.0)) * exp(-t * 9.5)
            val wave = sin(2.0 * PI * freq * t) * 0.74 + sin(2.0 * PI * freq * 3.0 * t) * 0.20 * exp(-t * 24.0)
            out[i] = ((wave * env * 0.85).coerceIn(-0.95, 0.95) * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }

    private fun synthOcarinaStartWhistle(): ShortArray {
        val duration = 0.44
        val total = (sfxSampleRate * duration).toInt()
        val out = ShortArray(total)
        for (i in 0 until total) {
            val t = i.toDouble() / sfxSampleRate
            val freq = if (t < 0.16) 783.99 else 1046.50 // G5 -> C6
            val localT = if (t < 0.16) t else t - 0.16
            val segLen = if (t < 0.16) 0.16 else 0.28
            val env = (1.0 - exp(-localT * 90.0)) * exp(-localT * (2.5 / segLen))
            val wave = sin(2.0 * PI * freq * t) * 0.82 + sin(2.0 * PI * freq * 2.0 * t) * 0.15
            out[i] = ((wave * env * 0.72).coerceIn(-0.94, 0.94) * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }

    private fun synthGrassRollTick(): ShortArray {
        val duration = 0.045
        val total = (sfxSampleRate * duration).toInt()
        val out = ShortArray(total)
        val rng = Random(88)
        for (i in 0 until total) {
            val t = i.toDouble() / sfxSampleRate
            val env = exp(-t * 85.0)
            val v = (sin(2.0 * PI * 320.0 * t) * 0.45 + rng.nextDouble(-0.4, 0.4)) * env
            out[i] = ((v * 0.65).coerceIn(-0.85, 0.85) * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }

    private fun synthHollowBallPop(): ShortArray {
        val duration = 0.15
        val total = (sfxSampleRate * duration).toInt()
        val out = ShortArray(total)
        for (i in 0 until total) {
            val t = i.toDouble() / sfxSampleRate
            val f = 420.0 + 380.0 * (t / duration)
            val env = (1.0 - exp(-t * 140.0)) * exp(-t * 28.0)
            val v = sin(2.0 * PI * f * t) * env * 0.82
            out[i] = (v.coerceIn(-0.94, 0.94) * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }

    private fun synthBrassPegClick(freq: Double): ShortArray {
        val duration = 0.068
        val total = (sfxSampleRate * duration).toInt()
        val out = ShortArray(total)
        for (i in 0 until total) {
            val t = i.toDouble() / sfxSampleRate
            val env = exp(-t * 62.0)
            val wood = sin(2.0 * PI * freq * t) * 0.68
            val brass = sin(2.0 * PI * (freq * 2.42) * t) * 0.32 * exp(-t * 90.0)
            out[i] = (((wood + brass) * env * 0.84).coerceIn(-0.95, 0.95) * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }

    private fun synthHeartbeat(intensity: Float): ShortArray {
        val duration = 0.44
        val total = (sfxSampleRate * duration).toInt()
        val out = ShortArray(total)
        for (i in 0 until total) {
            val t = i.toDouble() / sfxSampleRate
            val lub = if (t < 0.17) {
                val env = sin(PI * (t / 0.17)) * exp(-t * 11.0)
                val f = 68.0 - 20.0 * (t / 0.17)
                (sin(2.0 * PI * f * t) + 0.45 * sin(2.0 * PI * f * 2.0 * t)) * env
            } else 0.0

            val dub = if (t >= 0.16 && t < 0.40) {
                val dt = t - 0.16
                val env = sin(PI * (dt / 0.24)) * exp(-dt * 10.5)
                val f = 84.0 - 26.0 * (dt / 0.24)
                (sin(2.0 * PI * f * dt) + 0.55 * sin(2.0 * PI * f * 2.0 * dt)) * env * 1.18
            } else 0.0

            val sample = ((lub + dub) * 0.65 * intensity).coerceIn(-0.98, 0.98)
            out[i] = (sample * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }

    private fun synthSlowMoShimmer(): ShortArray {
        val duration = 0.72
        val total = (sfxSampleRate * duration).toInt()
        val out = ShortArray(total)
        val notes = listOf(1046.50, 987.77, 783.99, 659.25, 523.25)
        for (i in 0 until total) {
            val t = i.toDouble() / sfxSampleRate
            var acc = 0.0
            notes.forEachIndexed { idx, freq ->
                val onset = idx * 0.065
                if (t >= onset) {
                    val dt = t - onset
                    val env = exp(-dt * 5.0) * (1.0 - exp(-dt * 90.0))
                    acc += (sin(2.0 * PI * freq * dt) + 0.25 * sin(2.0 * PI * freq * 2.0 * dt)) * env * 0.25
                }
            }
            out[i] = (acc.coerceIn(-0.95, 0.95) * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }

    private fun synthTitaniumTeeBounce(): ShortArray {
        val duration = 0.28
        val total = (sfxSampleRate * duration).toInt()
        val out = ShortArray(total)
        for (i in 0 until total) {
            val t = i.toDouble() / sfxSampleRate
            val envMetal = exp(-t * 22.0)
            val envWood = exp(-t * 42.0)
            val ping = sin(2.0 * PI * 1760.0 * t) * 0.45 * envMetal +
                sin(2.0 * PI * 2637.0 * t) * 0.25 * envMetal +
                sin(2.0 * PI * 740.0 * t) * 0.40 * envWood
            out[i] = (ping.coerceIn(-0.95, 0.95) * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }

    private fun synthSoftGrassDrop(): ShortArray {
        val duration = 0.13
        val total = (sfxSampleRate * duration).toInt()
        val out = ShortArray(total)
        val rng = Random(42)
        for (i in 0 until total) {
            val t = i.toDouble() / sfxSampleRate
            val env = exp(-t * 36.0)
            val thud = sin(2.0 * PI * (190.0 - 70.0 * t) * t) * 0.55
            val rustle = (rng.nextDouble(-1.0, 1.0)) * 0.22
            out[i] = (((thud + rustle) * env).coerceIn(-0.9, 0.9) * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }

    private fun synthMarimbaNote(freq: Double, duration: Double): ShortArray {
        val total = (sfxSampleRate * duration).toInt()
        val out = ShortArray(total)
        for (i in 0 until total) {
            val t = i.toDouble() / sfxSampleRate
            val env = (1.0 - exp(-t * 140.0)) * exp(-t * 11.5)
            val wave = sin(2.0 * PI * freq * t) * 0.72 + sin(2.0 * PI * freq * 4.0 * t) * 0.22 * exp(-t * 28.0)
            out[i] = ((wave * env * 0.85).coerceIn(-0.95, 0.95) * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }

    private fun synthWoodBlock(freq: Double): ShortArray {
        val duration = 0.075
        val total = (sfxSampleRate * duration).toInt()
        val out = ShortArray(total)
        for (i in 0 until total) {
            val t = i.toDouble() / sfxSampleRate
            val env = exp(-t * 58.0)
            val wave = sin(2.0 * PI * freq * t) * 0.75 + sin(2.0 * PI * (freq * 1.58) * t) * 0.25
            out[i] = ((wave * env * 0.8).coerceIn(-0.95, 0.95) * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }

    private fun synthPuttImpact(): ShortArray {
        val duration = 0.15
        val total = (sfxSampleRate * duration).toInt()
        val out = ShortArray(total)
        for (i in 0 until total) {
            val t = i.toDouble() / sfxSampleRate
            val env = exp(-t * 44.0)
            val wave = sin(2.0 * PI * 520.0 * t) * 0.65 + sin(2.0 * PI * 1180.0 * t) * 0.35
            out[i] = ((wave * env * 0.88).coerceIn(-0.95, 0.95) * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }

    private fun synthWaterSplash(): ShortArray {
        val duration = 0.48
        val total = (sfxSampleRate * duration).toInt()
        val out = ShortArray(total)
        val rng = Random(77)
        var lp = 0.0
        for (i in 0 until total) {
            val t = i.toDouble() / sfxSampleRate
            val noise = rng.nextDouble(-1.0, 1.0)
            lp = lp * 0.82 + noise * 0.18
            val env = sin(PI * (t / duration).coerceIn(0.0, 1.0)) * exp(-t * 4.2)
            val bloop = if (t < 0.18) sin(2.0 * PI * (260.0 + 420.0 * t) * t) * exp(-t * 16.0) * 0.48 else 0.0
            val v = (lp * 1.4 * env + bloop).coerceIn(-0.95, 0.95)
            out[i] = (v * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }

    private fun synthGolfCupIn(): ShortArray {
        val duration = 0.45
        val total = (sfxSampleRate * duration).toInt()
        val out = ShortArray(total)
        for (i in 0 until total) {
            val t = i.toDouble() / sfxSampleRate
            val rattle1 = sin(2.0 * PI * 1480.0 * t) * exp(-t * 22.0) * 0.5
            val rattle2 = if (t >= 0.07) {
                val dt = t - 0.07
                (sin(2.0 * PI * 1975.0 * dt) * 0.55 + sin(2.0 * PI * 2637.0 * dt) * 0.3) * exp(-dt * 12.0)
            } else 0.0
            out[i] = ((rattle1 + rattle2).coerceIn(-0.95, 0.95) * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }

    private fun synthCardFlip(): ShortArray {
        val duration = 0.15
        val total = (sfxSampleRate * duration).toInt()
        val out = ShortArray(total)
        val rng = Random(101)
        for (i in 0 until total) {
            val t = i.toDouble() / sfxSampleRate
            val swoosh = rng.nextDouble(-1.0, 1.0) * sin(PI * (t / duration)) * 0.25
            val snap = if (t > 0.05) sin(2.0 * PI * 720.0 * (t - 0.05)) * exp(-(t - 0.05) * 48.0) * 0.55 else 0.0
            out[i] = ((swoosh + snap).coerceIn(-0.9, 0.9) * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }

    // 지브리 피아노·오케스트라 벨·스트링 메이저 9th 축하 팡파르
    private fun synthGhibliChordFanfare(): ShortArray {
        val duration = 1.95
        val total = (sfxSampleRate * duration).toInt()
        val out = ShortArray(total)
        val chord = listOf(523.25, 659.25, 783.99, 987.77, 1174.66, 1567.98)
        for (i in 0 until total) {
            val t = i.toDouble() / sfxSampleRate
            var sum = 0.0
            chord.forEachIndexed { idx, freq ->
                val onset = idx * 0.088
                if (t >= onset) {
                    val dt = t - onset
                    val attack = 1.0 - exp(-dt * 120.0)
                    val decay = exp(-dt * 2.2)
                    val tone = sin(2.0 * PI * freq * dt) * 0.68 +
                        sin(2.0 * PI * freq * 2.0 * dt) * 0.22 * exp(-dt * 6.0) +
                        sin(2.0 * PI * freq * 3.0 * dt) * 0.10 * exp(-dt * 10.0)
                    sum += tone * attack * decay * 0.24
                }
            }
            // 따뜻한 베이스 루트(C4) 패드 받침
            if (t >= 0.05) {
                val bt = t - 0.05
                sum += sin(2.0 * PI * 261.63 * bt) * (1.0 - exp(-bt * 40.0)) * exp(-bt * 1.9) * 0.18
            }
            out[i] = (sum.coerceIn(-0.96, 0.96) * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }

    // 커피·꼴등 당첨 발표용 익살스럽고 경쾌한 지브리 마림바·벨 팡파르
    private fun synthPlayfulPenaltyTune(): ShortArray {
        val duration = 1.75
        val total = (sfxSampleRate * duration).toInt()
        val out = ShortArray(total)
        val notes = listOf(
            Triple(0.00, 587.33, 0.16), // D5
            Triple(0.14, 659.25, 0.16), // E5
            Triple(0.28, 587.33, 0.16), // D5
            Triple(0.42, 493.88, 0.16), // B4
            Triple(0.58, 783.99, 0.95), // G5
            Triple(0.58, 987.77, 0.95), // B5
            Triple(0.58, 1174.66, 0.95) // D6
        )
        for (i in 0 until total) {
            val t = i.toDouble() / sfxSampleRate
            var sum = 0.0
            notes.forEach { (onset, freq, len) ->
                if (t >= onset && t <= onset + len + 0.30) {
                    val dt = t - onset
                    val env = (1.0 - exp(-dt * 135.0)) * exp(-dt * 3.8)
                    sum += (sin(2.0 * PI * freq * dt) + 0.28 * sin(2.0 * PI * freq * 2.0 * dt)) * env * 0.28
                }
            }
            out[i] = (sum.coerceIn(-0.96, 0.96) * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }
}
