package com.jjalddalmark.minigamerullet.audio

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
import androidx.compose.runtime.staticCompositionLocalOf
import com.jjalddalmark.minigamerullet.model.AppSettings
import com.jjalddalmark.minigamerullet.model.GameCategory
import com.jjalddalmark.minigamerullet.model.GolfGameType
import com.jjalddalmark.minigamerullet.model.RouletteDecisionSound
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * 화면별 어쿠스틱 힐링 사운드트랙 테마 5종
 */
enum class BgmTheme(val title: String) {
    HOME_MEADOW("햇살 머무는 초원 카페"),
    TEE_ORDER_BREEZE("아침 숲길 어쿠스틱 산책"),
    GREEN_TENSION_CAPER("호숫가 그늘집 재즈 라운지"),
    WINDMILL_WALTZ("언덕 위 목가적 풍차 세레나데"),
    MYSTIC_TAROT_CABIN("별빛 오두막 첼로와 하프")
}

/**
 * 게임 흐름과 감정에 맞춰 자연스럽게 크로스페이드되는 3단계 서사형 음악 악장
 */
enum class BgmMoodStage(
    val label: String,
    val description: String
) {
    CALM_INTRO(
        label = "평온한 시작",
        description = "나일론 기타·펠트 피아노·첼로가 호흡하는 포근한 도입부"
    ),
    PLAYING_PULSE(
        label = "설레는 두근거림",
        description = "따뜻한 카혼 심장박동 그루브와 피치카토가 어우러진 진행부"
    ),
    JOYFUL_FINALE(
        label = "기쁨의 결과",
        description = "메이저 9th 어쿠스틱 앙상블과 마림바가 피어나는 축하 악장"
    )
}

/**
 * 하위 게임 스테이지 스캐폴드에서 별도 보일러플레이트 없이 서사형 배경음 상태를 자동 동기화하기 위한 CompositionLocal
 */
val LocalSoundEngine = staticCompositionLocalOf<GhibliSoundEngine?> { null }

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

internal data class BgmTrackKey(
    val theme: BgmTheme,
    val mood: BgmMoodStage
)

/**
 * 배경음악 악보 노트 모델
 * instrument 코드:
 * - 0: 따뜻한 펠트 어쿠스틱 피아노 (Warm Felt Piano)
 * - 1: 핑거스타일 나일론 어쿠스틱 기타 (Fingerstyle Nylon Guitar)
 * - 2: 서정적 첼로 · 로우 우드윈드 (Warm Lyrical Cello)
 * - 3: 어쿠스틱 콘트라베이스 (Upright Acoustic Bass)
 * - 4: 따뜻한 로즈우드 마림바 · 로즈 일렉트릭 피아노 (Warm Rosewood Marimba)
 * - 5: 부드러운 카혼 심장박동(`midi=36`) & 브러시 우드 셰이커(`midi=42`)
 */
internal data class ScoreNote(
    val startBeat: Double,
    val durationBeats: Double,
    val midi: Int,
    val velocity: Double,
    val instrument: Int
)

internal data class BgmScoreData(
    val bpm: Double,
    val totalBeats: Double,
    val notes: List<ScoreNote>
)

/**
 * 귀가 편안한 중저음역(`MIDI 36~72`, `65Hz~523Hz`) 어쿠스틱 악기 모델링과
 * 16마디(`64박`, 약 `35~45초`) 기승전결(`A -> A' -> B -> C`) 장편 코드 진행을 합성하는 순수 Kotlin 사운드트랙 생성기
 */
internal object AcousticBgmSynthesizer {
    const val BGM_SAMPLE_RATE = 24000

    private val sineTable = FloatArray(4096) { idx ->
        sin(2.0 * PI * idx / 4096.0).toFloat()
    }

    private fun fastSin(turns: Double): Float {
        val idx = ((turns * 4096.0).toLong() and 4095L).toInt()
        return sineTable[idx]
    }

    fun midiToHz(midi: Int): Double = 440.0 * 2.0.pow((midi - 69) / 12.0)

    /**
     * 테마 및 3단계 서사 상태(`CALM_INTRO`, `PLAYING_PULSE`, `JOYFUL_FINALE`)에 맞는 64박 장편 악보 생성
     * 모든 선율과 화성은 고음 피로가 없는 중저음(`MIDI <= 72`) 안에 배치됨
     */
    fun buildScore(theme: BgmTheme, mood: BgmMoodStage): BgmScoreData {
        val totalBeats = 64.0 // 4/4박 기준 16마디 장편 구성 (짧은 구간 단순 반복 배제)
        val notes = ArrayList<ScoreNote>(720)

        // 테마별 기본 화성 진행 (16마디 = A 4마디 + A' 4마디 + B 브릿지 4마디 + C 여운 및 회귀 4마디)
        // 각 마디는 [베이스 루트, 내성1, 내성2, 텐션(9th/7th/6th)] 중저음 보이싱으로 구성
        val baseBars: List<List<Int>>
        val calmBpm: Double
        val pulseBpm: Double
        val joyBpm: Double
        val motifA: List<Triple<Double, Double, Int>>
        val motifAPrime: List<Triple<Double, Double, Int>>
        val motifBridge: List<Triple<Double, Double, Int>>
        val motifTurnaround: List<Triple<Double, Double, Int>>

        when (theme) {
            BgmTheme.HOME_MEADOW -> {
                // "햇살 머무는 초원 카페" — C Major / Fmaj9 어쿠스틱 카페 & 히사이시 서정 진행
                calmBpm = 88.0
                pulseBpm = 106.0
                joyBpm = 102.0
                baseBars = listOf(
                    // Section A (1~4마디): Fmaj9 - G6 - Em7 - Am9
                    listOf(41, 53, 57, 60, 64),
                    listOf(43, 55, 59, 62, 64),
                    listOf(40, 52, 55, 59, 62),
                    listOf(45, 52, 57, 60, 64),
                    // Section A' (5~8마디): Dm9 - G13sus4 - Cmaj9 - Gm7/C9
                    listOf(38, 50, 53, 57, 64),
                    listOf(43, 53, 57, 60, 64),
                    listOf(36, 48, 52, 55, 62),
                    listOf(48, 55, 58, 62, 64),
                    // Section B 브릿지 (9~12마디): Fmaj9 - Fm6(따뜻한 노스탤지어) - Em7 - A7sus4/A7
                    listOf(41, 53, 57, 60, 67),
                    listOf(41, 53, 56, 60, 62),
                    listOf(40, 52, 55, 59, 64),
                    listOf(45, 55, 57, 61, 64),
                    // Section C 회귀 (13~16마디): Dm9 - Em7 - Fmaj7 - G13sus4
                    listOf(38, 50, 53, 57, 64),
                    listOf(40, 52, 55, 59, 62),
                    listOf(41, 53, 57, 60, 64),
                    listOf(43, 53, 57, 60, 62)
                )
                motifA = listOf(
                    Triple(0.0, 1.5, 64), Triple(1.5, 0.5, 67), Triple(2.0, 1.5, 69), Triple(3.5, 0.5, 67),
                    Triple(4.0, 1.5, 67), Triple(5.5, 0.5, 64), Triple(6.0, 2.0, 62),
                    Triple(8.0, 1.0, 64), Triple(9.0, 1.0, 67), Triple(10.0, 1.5, 69), Triple(11.5, 0.5, 67),
                    Triple(12.0, 2.0, 64), Triple(14.5, 1.5, 60)
                )
                motifAPrime = listOf(
                    Triple(16.0, 1.5, 65), Triple(17.5, 0.5, 67), Triple(18.0, 1.5, 69), Triple(19.5, 0.5, 72),
                    Triple(20.0, 1.5, 71), Triple(21.5, 0.5, 69), Triple(22.0, 2.0, 67),
                    Triple(24.0, 1.5, 64), Triple(25.5, 0.5, 67), Triple(26.0, 2.0, 62),
                    Triple(28.0, 1.5, 64), Triple(29.5, 0.5, 65), Triple(30.0, 2.0, 67)
                )
                motifBridge = listOf(
                    Triple(32.0, 1.5, 69), Triple(33.5, 0.5, 72), Triple(34.0, 1.5, 69), Triple(35.5, 0.5, 67),
                    Triple(36.0, 1.5, 68), Triple(37.5, 0.5, 65), Triple(38.0, 2.0, 62),
                    Triple(40.0, 1.0, 64), Triple(41.0, 1.0, 67), Triple(42.0, 1.5, 71), Triple(43.5, 0.5, 69),
                    Triple(44.0, 1.5, 69), Triple(45.5, 0.5, 67), Triple(46.0, 2.0, 64)
                )
                motifTurnaround = listOf(
                    Triple(48.0, 1.5, 65), Triple(49.5, 0.5, 67), Triple(50.0, 1.5, 69), Triple(51.5, 0.5, 65),
                    Triple(52.0, 1.5, 67), Triple(53.5, 0.5, 64), Triple(54.0, 2.0, 62),
                    Triple(56.0, 1.0, 60), Triple(57.0, 1.0, 64), Triple(58.0, 1.5, 67), Triple(59.5, 0.5, 69),
                    Triple(60.0, 2.0, 67), Triple(62.0, 2.0, 62)
                )
            }

            BgmTheme.WINDMILL_WALTZ -> {
                // "언덕 위 목가적 풍차 세레나데" — F Major / Bbmaj9 포근한 유러피언 목가 어쿠스틱 세레나데
                calmBpm = 90.0
                pulseBpm = 108.0
                joyBpm = 104.0
                baseBars = listOf(
                    // Section A (1~4마디): Fmaj9 - C/E - Dm9 - Am7
                    listOf(41, 53, 57, 60, 67),
                    listOf(40, 52, 55, 60, 64),
                    listOf(38, 50, 53, 57, 64),
                    listOf(45, 52, 57, 60, 64),
                    // Section A' (5~8마디): Bbmaj9 - F/A - Gm9 - C13sus4
                    listOf(46, 53, 58, 62, 65),
                    listOf(45, 53, 57, 60, 65),
                    listOf(43, 50, 55, 58, 62),
                    listOf(48, 55, 58, 60, 65),
                    // Section B 브릿지 (9~12마디): Dm9 - Am7 - Bbmaj7 - Fmaj7
                    listOf(38, 50, 53, 57, 65),
                    listOf(45, 52, 57, 60, 67),
                    listOf(46, 53, 58, 62, 65),
                    listOf(41, 53, 57, 60, 65),
                    // Section C 회귀 (13~16마디): Gm9 - Bbm6(포근한 석양 화성) - F/C - C13sus4
                    listOf(43, 50, 55, 58, 65),
                    listOf(46, 53, 58, 61, 65),
                    listOf(48, 53, 57, 60, 65),
                    listOf(48, 55, 58, 62, 64)
                )
                motifA = listOf(
                    Triple(0.0, 1.5, 65), Triple(1.5, 0.5, 67), Triple(2.0, 1.5, 69), Triple(3.5, 0.5, 65),
                    Triple(4.0, 1.5, 67), Triple(5.5, 0.5, 64), Triple(6.0, 2.0, 60),
                    Triple(8.0, 1.5, 62), Triple(9.5, 0.5, 65), Triple(10.0, 1.5, 69), Triple(11.5, 0.5, 67),
                    Triple(12.0, 2.0, 65), Triple(14.5, 1.5, 64)
                )
                motifAPrime = listOf(
                    Triple(16.0, 1.5, 62), Triple(17.5, 0.5, 65), Triple(18.0, 1.5, 70), Triple(19.5, 0.5, 69),
                    Triple(20.0, 1.5, 69), Triple(21.5, 0.5, 65), Triple(22.0, 2.0, 60),
                    Triple(24.0, 1.0, 62), Triple(25.0, 1.0, 65), Triple(26.0, 1.5, 67), Triple(27.5, 0.5, 69),
                    Triple(28.0, 2.0, 67), Triple(30.0, 2.0, 65)
                )
                motifBridge = listOf(
                    Triple(32.0, 1.5, 69), Triple(33.5, 0.5, 70), Triple(34.0, 1.5, 72), Triple(35.5, 0.5, 69),
                    Triple(36.0, 1.5, 67), Triple(37.5, 0.5, 64), Triple(38.0, 2.0, 60),
                    Triple(40.0, 1.5, 62), Triple(41.5, 0.5, 65), Triple(42.0, 1.5, 70), Triple(43.5, 0.5, 69),
                    Triple(44.0, 2.0, 69), Triple(46.5, 1.5, 65)
                )
                motifTurnaround = listOf(
                    Triple(48.0, 1.5, 67), Triple(49.5, 0.5, 69), Triple(50.0, 1.5, 70), Triple(51.5, 0.5, 67),
                    Triple(52.0, 1.5, 68), Triple(53.5, 0.5, 65), Triple(54.0, 2.0, 61),
                    Triple(56.0, 1.5, 65), Triple(57.5, 0.5, 67), Triple(58.0, 2.0, 69),
                    Triple(60.0, 2.0, 67), Triple(62.0, 2.0, 64)
                )
            }

            BgmTheme.TEE_ORDER_BREEZE -> {
                // "아침 숲길 어쿠스틱 산책" — G Major 맑고 포근한 핑거스타일 기타 & 피아노
                calmBpm = 88.0
                pulseBpm = 108.0
                joyBpm = 104.0
                baseBars = listOf(
                    listOf(43, 55, 59, 62, 66), // Gmaj7
                    listOf(42, 54, 57, 62, 66), // D/F#
                    listOf(40, 52, 55, 59, 62), // Em7
                    listOf(47, 54, 57, 62, 66), // Bm7
                    listOf(48, 55, 59, 64, 67), // Cmaj7
                    listOf(47, 55, 59, 62, 67), // G/B
                    listOf(45, 52, 55, 60, 64), // Am7
                    listOf(38, 50, 55, 59, 62), // D13sus4
                    listOf(48, 55, 60, 64, 67), // Cmaj9
                    listOf(48, 55, 58, 63, 67), // Cm6
                    listOf(47, 54, 59, 62, 66), // Bm7
                    listOf(40, 52, 56, 59, 64), // E7(warm)
                    listOf(45, 52, 57, 60, 64), // Am9
                    listOf(47, 55, 59, 62, 67), // G/B
                    listOf(48, 55, 60, 64, 67), // Cmaj7
                    listOf(38, 50, 55, 57, 62)  // D9sus4
                )
                motifA = listOf(
                    Triple(0.0, 1.5, 62), Triple(1.5, 0.5, 64), Triple(2.0, 1.5, 67), Triple(3.5, 0.5, 71),
                    Triple(4.0, 1.5, 69), Triple(5.5, 0.5, 66), Triple(6.0, 2.0, 62),
                    Triple(8.0, 1.0, 64), Triple(9.0, 1.0, 67), Triple(10.0, 1.5, 71), Triple(11.5, 0.5, 69),
                    Triple(12.0, 2.0, 66), Triple(14.5, 1.5, 62)
                )
                motifAPrime = listOf(
                    Triple(16.0, 1.5, 64), Triple(17.5, 0.5, 67), Triple(18.0, 1.5, 71), Triple(19.5, 0.5, 69),
                    Triple(20.0, 1.5, 67), Triple(21.5, 0.5, 64), Triple(22.0, 2.0, 62),
                    Triple(24.0, 1.0, 60), Triple(25.0, 1.0, 64), Triple(26.0, 1.5, 67), Triple(27.5, 0.5, 69),
                    Triple(28.0, 2.0, 69), Triple(30.0, 2.0, 66)
                )
                motifBridge = listOf(
                    Triple(32.0, 1.5, 67), Triple(33.5, 0.5, 69), Triple(34.0, 1.5, 71), Triple(35.5, 0.5, 67),
                    Triple(36.0, 1.5, 67), Triple(37.5, 0.5, 63), Triple(38.0, 2.0, 60),
                    Triple(40.0, 1.5, 62), Triple(41.5, 0.5, 66), Triple(42.0, 2.0, 69),
                    Triple(44.0, 1.5, 68), Triple(45.5, 0.5, 64), Triple(46.0, 2.0, 59)
                )
                motifTurnaround = listOf(
                    Triple(48.0, 1.5, 60), Triple(49.5, 0.5, 64), Triple(50.0, 1.5, 67), Triple(51.5, 0.5, 69),
                    Triple(52.0, 1.5, 67), Triple(53.5, 0.5, 62), Triple(54.0, 2.0, 59),
                    Triple(56.0, 1.5, 60), Triple(57.5, 0.5, 64), Triple(58.0, 2.0, 67),
                    Triple(60.0, 2.0, 66), Triple(62.0, 2.0, 62)
                )
            }

            BgmTheme.GREEN_TENSION_CAPER -> {
                // "호숫가 그늘집 재즈 라운지" — 따뜻한 어쿠스틱 보사·스윙 라운지 (Dm9 - G13 - Cmaj9 - A7)
                calmBpm = 90.0
                pulseBpm = 110.0
                joyBpm = 105.0
                baseBars = listOf(
                    listOf(38, 50, 53, 57, 64), // Dm9
                    listOf(43, 53, 57, 59, 64), // G13
                    listOf(36, 48, 52, 55, 62), // Cmaj9
                    listOf(45, 52, 55, 60, 64), // Am9
                    listOf(38, 50, 53, 57, 64), // Dm9
                    listOf(40, 52, 56, 59, 62), // E7(warm)
                    listOf(45, 52, 57, 60, 64), // Am9
                    listOf(45, 55, 57, 61, 64), // A7
                    listOf(41, 53, 57, 60, 64), // Fmaj9
                    listOf(43, 55, 59, 62, 64), // G6
                    listOf(40, 52, 55, 59, 62), // Em7
                    listOf(45, 52, 57, 60, 64), // Am9
                    listOf(38, 50, 53, 57, 62), // Dm7
                    listOf(43, 53, 55, 59, 62), // G9sus4
                    listOf(36, 48, 52, 55, 62), // Cmaj9
                    listOf(40, 52, 55, 59, 62)  // Em7
                )
                motifA = listOf(
                    Triple(0.0, 1.0, 65), Triple(1.0, 1.0, 64), Triple(2.0, 1.5, 62), Triple(3.5, 0.5, 65),
                    Triple(4.0, 1.5, 64), Triple(5.5, 0.5, 62), Triple(6.0, 2.0, 59),
                    Triple(8.0, 1.5, 64), Triple(9.5, 0.5, 67), Triple(10.0, 2.0, 62),
                    Triple(12.0, 1.5, 60), Triple(13.5, 0.5, 64), Triple(14.0, 2.0, 67)
                )
                motifAPrime = listOf(
                    Triple(16.0, 1.0, 69), Triple(17.0, 1.0, 65), Triple(18.0, 1.5, 64), Triple(19.5, 0.5, 62),
                    Triple(20.0, 1.5, 64), Triple(21.5, 0.5, 68), Triple(22.0, 2.0, 71),
                    Triple(24.0, 1.5, 69), Triple(25.5, 0.5, 67), Triple(26.0, 2.0, 64),
                    Triple(28.0, 1.5, 67), Triple(29.5, 0.5, 69), Triple(30.0, 2.0, 64)
                )
                motifBridge = listOf(
                    Triple(32.0, 1.5, 69), Triple(33.5, 0.5, 67), Triple(34.0, 1.5, 65), Triple(35.5, 0.5, 69),
                    Triple(36.0, 1.5, 67), Triple(37.5, 0.5, 64), Triple(38.0, 2.0, 62),
                    Triple(40.0, 1.0, 64), Triple(41.0, 1.0, 67), Triple(42.0, 2.0, 71),
                    Triple(44.0, 1.5, 69), Triple(45.5, 0.5, 67), Triple(46.0, 2.0, 64)
                )
                motifTurnaround = listOf(
                    Triple(48.0, 1.5, 65), Triple(49.5, 0.5, 67), Triple(50.0, 1.5, 69), Triple(51.5, 0.5, 65),
                    Triple(52.0, 1.5, 67), Triple(53.5, 0.5, 64), Triple(54.0, 2.0, 62),
                    Triple(56.0, 2.0, 64), Triple(58.5, 1.5, 60),
                    Triple(60.0, 2.0, 62), Triple(62.0, 2.0, 59)
                )
            }

            BgmTheme.MYSTIC_TAROT_CABIN -> {
                // "별빛 오두막 첼로와 하프" — 고음 벨을 배제한 포근한 나일론 하프 & 서정 첼로 녹턴
                calmBpm = 86.0
                pulseBpm = 104.0
                joyBpm = 100.0
                baseBars = listOf(
                    listOf(45, 52, 57, 60, 64), // Am9
                    listOf(40, 52, 55, 59, 64), // Em7
                    listOf(41, 53, 57, 60, 64), // Fmaj7
                    listOf(36, 48, 52, 55, 60), // Cmaj7
                    listOf(38, 50, 53, 57, 62), // Dm7
                    listOf(45, 52, 57, 60, 64), // Am7
                    listOf(41, 53, 57, 62, 65), // Dm/F
                    listOf(40, 52, 57, 59, 64), // E7sus4
                    listOf(41, 53, 57, 60, 64), // Fmaj9
                    listOf(43, 55, 59, 62, 67), // G6
                    listOf(36, 48, 52, 55, 64), // Cmaj9
                    listOf(45, 52, 57, 60, 64), // Am9
                    listOf(38, 50, 53, 57, 64), // Dm9
                    listOf(43, 53, 55, 59, 62), // G13sus4
                    listOf(36, 48, 52, 55, 62), // Cmaj9
                    listOf(40, 52, 56, 59, 64)  // E7(warm)
                )
                motifA = listOf(
                    Triple(0.0, 2.0, 64), Triple(2.0, 1.5, 67), Triple(3.5, 0.5, 69),
                    Triple(4.0, 2.0, 67), Triple(6.0, 2.0, 64),
                    Triple(8.0, 1.5, 65), Triple(9.5, 0.5, 64), Triple(10.0, 2.0, 60),
                    Triple(12.0, 2.0, 64), Triple(14.5, 1.5, 62)
                )
                motifAPrime = listOf(
                    Triple(16.0, 1.5, 62), Triple(17.5, 0.5, 65), Triple(18.0, 2.0, 69),
                    Triple(20.0, 2.0, 67), Triple(22.0, 2.0, 64),
                    Triple(24.0, 1.5, 65), Triple(25.5, 0.5, 62), Triple(26.0, 2.0, 60),
                    Triple(28.0, 2.0, 59), Triple(30.0, 2.0, 64)
                )
                motifBridge = listOf(
                    Triple(32.0, 1.5, 65), Triple(33.5, 0.5, 67), Triple(34.0, 2.0, 69),
                    Triple(36.0, 1.5, 71), Triple(37.5, 0.5, 69), Triple(38.0, 2.0, 67),
                    Triple(40.0, 1.5, 67), Triple(41.5, 0.5, 64), Triple(42.0, 2.0, 60),
                    Triple(44.0, 2.0, 64), Triple(46.0, 2.0, 67)
                )
                motifTurnaround = listOf(
                    Triple(48.0, 1.5, 65), Triple(49.5, 0.5, 67), Triple(50.0, 2.0, 69),
                    Triple(52.0, 1.5, 67), Triple(53.5, 0.5, 65), Triple(54.0, 2.0, 62),
                    Triple(56.0, 2.0, 64), Triple(58.0, 2.0, 60),
                    Triple(60.0, 2.0, 59), Triple(62.0, 2.0, 64)
                )
            }
        }

        val fullMelody = motifA + motifAPrime + motifBridge + motifTurnaround
        val bpm: Double

        when (mood) {
            BgmMoodStage.CALM_INTRO -> {
                bpm = calmBpm
                // 1단계 [평온한 시작]: 나일론 기타 핑거스타일 아르페지오 + 부드러운 펠트 피아노 + 서정적 첼로 호흡
                baseBars.forEachIndexed { barIdx, chord ->
                    val b0 = barIdx * 4.0
                    val root = chord[0].coerceIn(36, 50)
                    // 포근한 어쿠스틱 베이스 근음 (2분음표 호흡)
                    notes.add(ScoreNote(b0, 2.4, root, 0.30, 3))
                    if (barIdx >= 4) {
                        notes.add(ScoreNote(b0 + 2.0, 1.8, (chord[1] - 12).coerceIn(38, 52), 0.22, 3))
                    }

                    // 핑거스타일 나일론 기타 8분음표 패턴 (1 - 2 - 3 - 4 - 3 - 2 - 3 - 4)
                    val patternIndices = intArrayOf(1, 2, 3, 4, 2, 3, 4, 2)
                    for (step in 0 until 8) {
                        val tone = chord[patternIndices[step].coerceAtMost(chord.lastIndex)].coerceIn(48, 67)
                        val humanVel = if (step % 4 == 0) 0.21 else if (step % 2 == 0) 0.16 else 0.13
                        notes.add(ScoreNote(b0 + step * 0.5, 0.95, tone, humanVel, 1))
                    }

                    // 펠트 피아노 따뜻한 패드 화음 (마디 첫 박과 셋째 박에 살며시 터치)
                    notes.add(ScoreNote(b0, 2.2, chord[2].coerceIn(50, 65), 0.15, 0))
                    notes.add(ScoreNote(b0 + 2.0, 1.8, chord[3].coerceIn(52, 67), 0.13, 0))
                }

                // 주선율: 전반부는 따뜻한 펠트 피아노와 나일론 기타, 후반부(5마디~)는 첼로가 감미롭게 노래함
                fullMelody.forEach { (beat, dur, rawMidi) ->
                    val safeMidi = rawMidi.coerceIn(55, 71)
                    if (beat < 16.0) {
                        notes.add(ScoreNote(beat, dur * 0.96, safeMidi, 0.24, 0))
                        notes.add(ScoreNote(beat + 0.02, dur * 0.90, safeMidi, 0.16, 1))
                    } else {
                        // 한 옥타브 아래 따뜻한 중저음 첼로 선율 + 부드러운 펠트 피아노 유니즌/화음
                        notes.add(ScoreNote(beat, dur * 0.98, (safeMidi - 12).coerceIn(48, 62), 0.24, 2))
                        notes.add(ScoreNote(beat, dur * 0.92, safeMidi, 0.20, 0))
                    }
                }
            }

            BgmMoodStage.PLAYING_PULSE -> {
                bpm = pulseBpm
                // 2단계 [설레는 두근거림]: 따뜻한 카혼 심장박동 리듬 + 브러시 셰이커 + 피치카토 워킹 베이스 + 중독성 있는 싱코페이션 훅
                baseBars.forEachIndexed { barIdx, chord ->
                    val b0 = barIdx * 4.0
                    val root = chord[0].coerceIn(36, 50)
                    val fifth = (root + 7).coerceIn(41, 55)

                    // '두근-두근' 설레는 저역 카혼 심장박동 그루브 (0.0, 0.75, 2.0, 2.75 박자 싱코페이션)
                    notes.add(ScoreNote(b0 + 0.0, 0.45, 36, 0.32, 5))
                    notes.add(ScoreNote(b0 + 0.75, 0.38, 36, 0.22, 5))
                    notes.add(ScoreNote(b0 + 2.0, 0.45, 36, 0.30, 5))
                    if (barIdx % 2 == 1) {
                        notes.add(ScoreNote(b0 + 2.75, 0.38, 36, 0.24, 5))
                    }

                    // 부드러운 브러시 셰이커 8분 리듬
                    for (s in 0 until 8) {
                        val shakerVel = if (s % 2 == 1) 0.16 else 0.09
                        notes.add(ScoreNote(b0 + s * 0.5, 0.22, 42, shakerVel, 5))
                    }

                    // 통통 튀는 어쿠스틱 피치카토 워킹 베이스
                    notes.add(ScoreNote(b0 + 0.0, 0.85, root, 0.34, 3))
                    notes.add(ScoreNote(b0 + 1.0, 0.70, fifth, 0.25, 3))
                    notes.add(ScoreNote(b0 + 2.0, 0.85, root, 0.31, 3))
                    notes.add(ScoreNote(b0 + 3.0, 0.75, (chord[1] - 12).coerceIn(38, 53), 0.27, 3))

                    // 리드미컬한 어쿠스틱 기타 & 펠트 피아노 컴핑 (오프비트 싱코페이션으로 두근거리는 추진력 부여)
                    val compOffsets = doubleArrayOf(0.0, 0.75, 1.5, 2.5, 3.25)
                    for (idx in compOffsets.indices) {
                        val off = compOffsets[idx]
                        val v = if (idx == 0 || idx == 3) 0.19 else 0.14
                        notes.add(ScoreNote(b0 + off, 0.55, chord[2].coerceIn(50, 64), v, 1))
                        notes.add(ScoreNote(b0 + off, 0.55, chord[3].coerceIn(53, 67), v * 0.85, 0))
                    }
                }

                // 주선율: 따뜻한 로즈우드 마림바와 나일론 기타가 주고받으며 기대감과 설렘을 고조시킴
                fullMelody.forEachIndexed { idx, (beat, dur, rawMidi) ->
                    val safeMidi = rawMidi.coerceIn(55, 71)
                    notes.add(ScoreNote(beat, dur * 0.86, safeMidi, 0.25, 4))
                    notes.add(ScoreNote(beat, dur * 0.82, safeMidi, 0.16, 1))
                    // 브릿지 구간(32박~)부터 따뜻한 첼로 서스테인 대선율을 더해 클라이맥스 몰입감 강화
                    if (beat >= 32.0 && idx % 2 == 0) {
                        notes.add(ScoreNote(beat, (dur * 1.3).coerceAtMost(2.5), (safeMidi - 12).coerceIn(48, 62), 0.19, 2))
                    }
                }
            }

            BgmMoodStage.JOYFUL_FINALE -> {
                bpm = joyBpm
                // 3단계 [기쁨의 결과]: 메이저 9th 풀 어쿠스틱 앙상블! 밝고 포근한 기타 스트로크 + 마림바 + 첼로 축하 코러스
                val joyfulBars = if (theme == BgmTheme.MYSTIC_TAROT_CABIN || theme == BgmTheme.GREEN_TENSION_CAPER) {
                    // 단조 계열 테마도 결과가 나왔을 때는 환하고 따뜻한 장조(Fmaj9 - G6 - Em7 - Am9 - Dm9 - G13 - Cmaj9)로 피어남
                    listOf(
                        listOf(41, 53, 57, 60, 64),
                        listOf(43, 55, 59, 62, 67),
                        listOf(40, 52, 55, 59, 64),
                        listOf(45, 52, 57, 60, 64),
                        listOf(38, 50, 53, 57, 64),
                        listOf(43, 53, 57, 59, 64),
                        listOf(36, 48, 52, 55, 64),
                        listOf(48, 55, 58, 62, 64),
                        listOf(41, 53, 57, 60, 67),
                        listOf(43, 55, 59, 62, 67),
                        listOf(40, 52, 55, 59, 64),
                        listOf(45, 52, 57, 60, 64),
                        listOf(38, 50, 53, 57, 64),
                        listOf(41, 53, 56, 60, 62),
                        listOf(36, 48, 52, 55, 62),
                        listOf(43, 53, 57, 60, 62)
                    )
                } else {
                    baseBars
                }

                joyfulBars.forEachIndexed { barIdx, chord ->
                    val b0 = barIdx * 4.0
                    val root = chord[0].coerceIn(36, 50)
                    val fifth = (root + 7).coerceIn(41, 55)

                    // 전반 12마디는 경쾌하고 포근한 축하 리듬, 마지막 4마디(13~16마디)는 평온한 여운으로 자연스럽게 숨을 고름
                    if (barIdx < 12) {
                        notes.add(ScoreNote(b0 + 0.0, 0.45, 36, 0.25, 5))
                        notes.add(ScoreNote(b0 + 2.0, 0.45, 36, 0.22, 5))
                        for (s in 0 until 8) {
                            if (s % 2 == 1) {
                                notes.add(ScoreNote(b0 + s * 0.5, 0.20, 42, 0.13, 5))
                            }
                        }
                    }

                    notes.add(ScoreNote(b0 + 0.0, 1.6, root, 0.32, 3))
                    notes.add(ScoreNote(b0 + 2.0, 1.6, fifth, 0.26, 3))

                    // 밝고 따뜻한 어쿠스틱 나일론 기타 스트로크(살짝 시간차를 둔 아르페지오 스트럼)
                    val strumBeats = if (barIdx < 12) listOf(0.0, 1.0, 1.5, 2.5, 3.0) else listOf(0.0, 2.0)
                    strumBeats.forEachIndexed { sIdx, sb ->
                        val baseVel = if (sIdx == 0) 0.18 else 0.13
                        notes.add(ScoreNote(b0 + sb, 0.85, chord[1].coerceIn(48, 62), baseVel, 1))
                        notes.add(ScoreNote(b0 + sb + 0.025, 0.85, chord[2].coerceIn(50, 64), baseVel, 1))
                        notes.add(ScoreNote(b0 + sb + 0.050, 0.85, chord[4].coerceIn(55, 69), baseVel * 0.92, 0))
                    }
                }

                // 기쁨의 축하 선율: 로즈우드 마림바 + 펠트 피아노 + 따뜻한 3도/6도 아래 첼로 하모니
                fullMelody.forEach { (beat, dur, rawMidi) ->
                    val joyfulMidi = (rawMidi + if (theme == BgmTheme.MYSTIC_TAROT_CABIN) 2 else 0).coerceIn(57, 72)
                    notes.add(ScoreNote(beat, dur * 0.95, joyfulMidi, 0.24, 4))
                    notes.add(ScoreNote(beat, dur * 0.95, joyfulMidi, 0.19, 0))
                    notes.add(ScoreNote(beat, dur * 0.98, (joyfulMidi - 12).coerceIn(48, 62), 0.22, 2))
                }
            }
        }

        return BgmScoreData(
            bpm = bpm,
            totalBeats = totalBeats,
            notes = notes
        )
    }

    /**
     * 악보 데이터를 입력받아 귀가 편안한 2단 저역통과 필터(`~1.28kHz`)와 원목 공간 잔향이 입혀진 16-bit PCM 루프 배열로 합성
     */
    fun synthesizeTrackPcm(theme: BgmTheme, mood: BgmMoodStage): ShortArray {
        val score = buildScore(theme, mood)
        val secPerBeat = 60.0 / score.bpm
        val loopSeconds = score.totalBeats * secPerBeat
        val tailSeconds = 1.35
        val loopSamples = (BGM_SAMPLE_RATE * loopSeconds).toInt()
        val tailSamples = (BGM_SAMPLE_RATE * tailSeconds).toInt()
        val mixBuf = FloatArray(loopSamples + tailSamples)
        val shakerRng = Random(theme.ordinal * 31 + mood.ordinal * 17 + 2026)

        for (note in score.notes) {
            val startSec = note.startBeat * secPerBeat
            val durSec = note.durationBeats * secPerBeat
            val startSample = (startSec * BGM_SAMPLE_RATE).toInt().coerceAtLeast(0)
            if (startSample >= mixBuf.size) continue

            val releaseTail = when (note.instrument) {
                2 -> 0.65
                5 -> 0.16
                else -> 0.55
            }
            val endSample = ((startSec + durSec + releaseTail) * BGM_SAMPLE_RATE)
                .toInt()
                .coerceAtMost(mixBuf.size)
            val vel = note.velocity.toFloat()

            if (note.instrument == 5) {
                // 부드러운 어쿠스틱 퍼커션 (고음 치찰음이 전혀 없는 카혼 저음 박동 & 우드 브러시 셰이커)
                if (note.midi <= 38) {
                    // 카혼 저음 심장박동 (64Hz -> 44Hz 포근한 원목 공명)
                    var phase = 0.0
                    for (s in startSample until endSample) {
                        val dt = (s - startSample).toDouble() / BGM_SAMPLE_RATE
                        if (dt > 0.26) break
                        val attack = (dt / 0.008).coerceAtMost(1.0)
                        val env = (attack * exp(-dt * 15.5)).toFloat()
                        val f = 64.0 - 20.0 * (dt / 0.26).coerceIn(0.0, 1.0)
                        phase += f / BGM_SAMPLE_RATE
                        val thump = fastSin(phase) * 0.76f + fastSin(phase * 2.0) * 0.24f
                        mixBuf[s] += thump * env * vel
                    }
                } else {
                    // 부드러운 대역제한 브러시 셰이커 (거친 고역 화이트노이즈를 걸러낸 포근한 모래결 질감)
                    var lp1 = 0f
                    var lp2 = 0f
                    var prevLp = 0f
                    for (s in startSample until endSample) {
                        val dt = (s - startSample).toDouble() / BGM_SAMPLE_RATE
                        if (dt > 0.15) break
                        val env = (sin(PI * (dt / 0.15).coerceIn(0.0, 1.0)) * exp(-dt * 18.0)).toFloat()
                        val raw = shakerRng.nextFloat() * 2f - 1f
                        lp1 += 0.24f * (raw - lp1)
                        lp2 += 0.24f * (lp1 - lp2)
                        val band = lp2 - prevLp * 0.82f
                        prevLp = lp2
                        mixBuf[s] += band * env * vel * 0.72f
                    }
                }
                continue
            }

            // 선율·화성 악기 주파수 안전 상한 보장 (절대 고음역 진입 불가)
            val clampedMidi = note.midi.coerceIn(34, 72)
            val freq = midiToHz(clampedMidi)
            var phase = 0.0
            var detunePhase = 0.13 // 미세 디튠 코러스로 스튜디오 어쿠스틱 현의 따스한 입체감 구현
            val phaseStep = freq / BGM_SAMPLE_RATE
            val detuneStep = (freq * 1.0018) / BGM_SAMPLE_RATE

            for (s in startSample until endSample) {
                val dt = (s - startSample).toDouble() / BGM_SAMPLE_RATE
                val sampleVal: Float = when (note.instrument) {
                    0 -> {
                        // 0: 따뜻한 펠트 어쿠스틱 피아노 (부드러운 18ms 해머 어택, 고배음 급속 감쇠)
                        val attack = (0.5 - 0.5 * kotlin.math.cos(PI * (dt / 0.018).coerceAtMost(1.0))).toFloat()
                        val release = if (dt <= durSec) {
                            exp(-dt * 2.1).toFloat()
                        } else {
                            (exp(-durSec * 2.1) * exp(-(dt - durSec) * 9.5)).toFloat()
                        }
                        val body = (fastSin(phase) + fastSin(detunePhase) * 0.45f) * 0.56f +
                            fastSin(phase * 2.0) * 0.18f * exp(-dt * 5.2).toFloat() +
                            fastSin(phase * 3.0) * 0.04f * exp(-dt * 11.0).toFloat()
                        body * attack * release * vel
                    }

                    1 -> {
                        // 1: 핑거스타일 나일론 어쿠스틱 기타 (손끝 살결로 튕긴 둥글고 포근한 울림)
                        val attack = (0.5 - 0.5 * kotlin.math.cos(PI * (dt / 0.011).coerceAtMost(1.0))).toFloat()
                        val decay = if (dt <= durSec) {
                            exp(-dt * 3.1).toFloat()
                        } else {
                            (exp(-durSec * 3.1) * exp(-(dt - durSec) * 11.0)).toFloat()
                        }
                        val nylon = (fastSin(phase) + fastSin(detunePhase) * 0.35f) * 0.60f +
                            fastSin(phase * 2.0) * 0.22f * exp(-dt * 6.5).toFloat() +
                            fastSin(phase * 3.0) * 0.05f * exp(-dt * 14.0).toFloat()
                        nylon * attack * decay * vel
                    }

                    2 -> {
                        // 2: 서정적 첼로 · 로우 우드윈드 (110ms 숨결 어택 + 은은한 비브라토)
                        val attack = (0.5 - 0.5 * kotlin.math.cos(PI * (dt / 0.11).coerceAtMost(1.0))).toFloat()
                        val sustain = if (dt <= durSec) {
                            1f
                        } else {
                            exp(-(dt - durSec) * 6.8).toFloat()
                        }
                        val vibDepth = if (dt > 0.16) 0.0020 * ((dt - 0.16) / 0.25).coerceAtMost(1.0) else 0.0
                        val vibMod = 1.0 + vibDepth * sin(2.0 * PI * 4.6 * dt)
                        val cello = fastSin(phase * vibMod) * 0.66f +
                            fastSin(phase * 2.0 * vibMod) * 0.24f +
                            fastSin(phase * 3.0 * vibMod) * 0.08f
                        cello * attack * sustain * vel
                    }

                    3 -> {
                        // 3: 어쿠스틱 콘트라베이스 (스마트폰 스피커에서도 깊이 들리는 2·3배음 원목 저음)
                        val attack = (dt / 0.015).coerceAtMost(1.0).toFloat()
                        val env = attack * exp(-dt * 4.4).toFloat()
                        val bass = fastSin(phase) * 0.56f +
                            fastSin(phase * 2.0) * 0.32f * exp(-dt * 3.5).toFloat() +
                            fastSin(phase * 3.0) * 0.12f * exp(-dt * 7.0).toFloat()
                        bass * env * vel
                    }

                    else -> {
                        // 4: 따뜻한 로즈우드 마림바 · 로즈 일렉트릭 피아노 (금속성 3배음 없이 둥글고 경쾌한 울림)
                        val attack = (0.5 - 0.5 * kotlin.math.cos(PI * (dt / 0.014).coerceAtMost(1.0))).toFloat()
                        val env = attack * exp(-dt * 4.8).toFloat()
                        val marimba = (fastSin(phase) + fastSin(detunePhase) * 0.30f) * 0.66f +
                            fastSin(phase * 2.0) * 0.16f * exp(-dt * 9.0).toFloat()
                        marimba * env * vel
                    }
                }
                mixBuf[s] += sampleVal
                phase += phaseStep
                detunePhase += detuneStep
            }
        }

        // 1단계: 루프 끝 잔향(Tail)을 루프 시작부로 폴딩하여 이음새 단절 완전 제거
        for (i in 0 until tailSamples) {
            if (i < loopSamples) {
                mixBuf[i] += mixBuf[loopSamples + i]
            }
        }

        // 2단계: 포근한 원목 공간 초기 반사음(Early Reflections: 31ms, 53ms, 79ms) 블렌딩으로 건조함 제거
        val tap1 = (0.031 * BGM_SAMPLE_RATE).toInt()
        val tap2 = (0.053 * BGM_SAMPLE_RATE).toInt()
        val tap3 = (0.079 * BGM_SAMPLE_RATE).toInt()
        val roomBuf = FloatArray(loopSamples)
        for (i in 0 until loopSamples) {
            val i1 = (i - tap1 + loopSamples) % loopSamples
            val i2 = (i - tap2 + loopSamples) % loopSamples
            val i3 = (i - tap3 + loopSamples) % loopSamples
            roomBuf[i] = mixBuf[i] * 0.82f +
                mixBuf[i1] * 0.11f +
                mixBuf[i2] * 0.07f +
                mixBuf[i3] * 0.045f
        }

        // 3단계: 2단 캐스케이드 저역통과 필터(컷오프 약 1.28kHz) + 아날로그 소프트 새츄레이션
        // 워밍업 패스(마지막 2400샘플 선행 통과)로 루프 0번 샘플과 끝 샘플의 필터 상태까지 완벽 일치시킴
        var lpStage1 = 0f
        var lpStage2 = 0f
        val alpha = 0.27f // 24kHz 기준 2단 적용 시 약 1.25~1.30kHz 이상 자극적 고역 완만 감쇠
        val warmStart = (loopSamples - 2400).coerceAtLeast(0)
        for (i in warmStart until loopSamples) {
            lpStage1 += alpha * (roomBuf[i] - lpStage1)
            lpStage2 += alpha * (lpStage1 - lpStage2)
        }

        val out = ShortArray(loopSamples)
        for (i in 0 until loopSamples) {
            lpStage1 += alpha * (roomBuf[i] - lpStage1)
            lpStage2 += alpha * (lpStage1 - lpStage2)
            val driven = lpStage2 * 0.88f
            // 부드러운 유리함수 소프트 클리핑 (tanh 근사: 피크를 둥글게 감싸 귀를 보호)
            val softSat = driven / (1f + abs(driven) * 0.55f)
            out[i] = (softSat.coerceIn(-0.92f, 0.92f) * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }
}

/**
 * 귀와 마음이 편안해지는 중저음 어쿠스틱 3단계 서사형 배경음악(BGM)과 효과음을 실시간 합성·재생하고
 * 휴대폰 하드웨어 미디어 볼륨(내장·외장·블루투스)과 무음 모드를 즉각 반영하는 오디오 엔진
 */
class GhibliSoundEngine(context: Context) {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val bgmMutex = Mutex()

    private val sfxSampleRate = 44100
    private val bgmSampleRate = AcousticBgmSynthesizer.BGM_SAMPLE_RATE

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

    @Volatile
    private var desiredBgmMood: BgmMoodStage = BgmMoodStage.CALM_INTRO

    private var activeBgmKey: BgmTrackKey? = null
    private var activeBgmTrack: AudioTrack? = null
    private val fadingOutTracks = mutableListOf<AudioTrack>()
    private var transitionJob: Job? = null

    private val _systemAudioState = MutableStateFlow(readSystemAudioSnapshot())
    val systemAudioState: StateFlow<SystemAudioSnapshot> = _systemAudioState.asStateFlow()

    private val _bgmMoodState = MutableStateFlow(BgmMoodStage.CALM_INTRO)
    val bgmMoodState: StateFlow<BgmMoodStage> = _bgmMoodState.asStateFlow()

    private val _activeThemeState = MutableStateFlow(BgmTheme.HOME_MEADOW)
    val activeThemeState: StateFlow<BgmTheme> = _activeThemeState.asStateFlow()

    // 사전 합성된 효과음 PCM 캐시 (고음 자극을 낮춘 따뜻한 원목·로즈우드 톤)
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
    private var diceCupShakePcm: ShortArray? = null
    private var diceBounceLowPcm: ShortArray? = null
    private var diceBounceHighPcm: ShortArray? = null
    private var yutSticksClackPcm: ShortArray? = null
    private var yutBonusCheerPcm: ShortArray? = null
    private var tarotSpreadPcm: ShortArray? = null
    private var tarotChimeHighPcm: ShortArray? = null
    private var tarotChimeCalmPcm: ShortArray? = null
    private var heartbeatNormalPcm: ShortArray? = null
    private var heartbeatIntensePcm: ShortArray? = null
    private var slowMoEnterPcm: ShortArray? = null
    private var ghibliFanfarePcm: ShortArray? = null
    private var penaltyRevealPcm: ShortArray? = null
    private var rouletteDrumHitPcm: ShortArray? = null
    private var rouletteWoodClapperPcm: ShortArray? = null
    private var rouletteTwistSweepPcm: ShortArray? = null

    // (테마, 서사단계) 조합별 장편 어쿠스틱 PCM 캐시
    private val bgmPcmMap = ConcurrentHashMap<BgmTrackKey, ShortArray>()

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

        scope.launch {
            heartbeatNormalPcm = synthHeartbeat(intensity = 0.75f)
            heartbeatIntensePcm = synthHeartbeat(intensity = 1.0f)
            slowMoEnterPcm = synthSlowMoShimmer()
            gameStartOrderPcm = synthGameCurtainStart(brightMajor = true)
            gameStartPenaltyPcm = synthGameCurtainStart(brightMajor = false)
            teeTossStartPcm = synthAiryWhoosh(duration = 0.20, startFreq = 260.0, endFreq = 540.0)
            teeBouncePcm = synthTitaniumTeeBounce()
            spinWhooshLowPcm = synthBambooSpinTick(freq = 410.0)
            spinWhooshHighPcm = synthBambooSpinTick(freq = 520.0)
            grassLandPcm = synthSoftGrassDrop()
            stickCaseShufflePcm = synthWoodenRattleSequence(count = 5, baseFreq = 390.0)

            // 기존 고음역을 한 옥타브 낮춘 따뜻한 로즈우드 5음계 (C4~C5: 261Hz~523Hz)
            val warmPentatonic = listOf(261.63, 293.66, 329.63, 392.00, 440.00, 523.25)
            warmPentatonic.forEachIndexed { idx, f ->
                stickSlideNotes[idx] = synthMarimbaNote(f, 0.28)
            }
            // 기존 1~2kHz 벨 대신 귀가 편안한 392Hz~784Hz 따뜻한 우드 하프 톤으로 완화
            val warmBellScale = listOf(392.00, 440.00, 493.88, 523.25, 587.33, 659.25)
            warmBellScale.forEachIndexed { idx, f ->
                notchRevealNotes[idx] = synthCelestaBell(f, 0.34)
            }

            ladderWhistlePcm = synthOcarinaStartWhistle()
            woodTickLowPcm = synthWoodBlock(freq = 460.0)
            woodTickHighPcm = synthWoodBlock(freq = 610.0)
            puttStrokePcm = synthPuttImpact()
            ballRollTickPcm = synthGrassRollTick()
            waterSplashPcm = synthWaterSplash()
            cupInPcm = synthGolfCupIn()
            basketShufflePcm = synthWoodenRattleSequence(count = 4, baseFreq = 460.0)
            ballInspectPopPcm = synthHollowBallPop()
            windmillStartPcm = synthAiryWhoosh(duration = 0.32, startFreq = 210.0, endFreq = 460.0)
            roulettePegLowPcm = synthBrassPegClick(freq = 540.0)
            roulettePegHighPcm = synthBrassPegClick(freq = 680.0)
            tileShufflePcm = synthWoodenRattleSequence(count = 6, baseFreq = 420.0)
            cardFlipPcm = synthCardFlip()
            tileTurnWhooshPcm = synthAiryWhoosh(duration = 0.28, startFreq = 240.0, endFreq = 490.0)
            diceCupShakePcm = synthWoodenRattleSequence(count = 7, baseFreq = 510.0)
            diceBounceLowPcm = synthWoodBlock(freq = 520.0)
            diceBounceHighPcm = synthWoodBlock(freq = 690.0)
            yutSticksClackPcm = synthYutFourStickClack()
            yutBonusCheerPcm = synthYutBonusBell()
            tarotSpreadPcm = synthTarotSilkSpread()
            tarotChimeHighPcm = synthTarotCelestaArpeggio(bright = true)
            tarotChimeCalmPcm = synthTarotCelestaArpeggio(bright = false)
            ghibliFanfarePcm = synthGhibliChordFanfare()
            penaltyRevealPcm = synthPlayfulPenaltyTune()
            rouletteDrumHitPcm = synthRouletteDrumRollHit()
            rouletteWoodClapperPcm = synthRouletteWoodClapper()
            rouletteTwistSweepPcm = synthRouletteTwistSweep()

            // 1순위: 로비 평온 인트로 즉시 합성 및 재생 시작
            getOrSynthBgmPcm(BgmTrackKey(BgmTheme.HOME_MEADOW, BgmMoodStage.CALM_INTRO))
            requestBgmTransition()

            // 2순위: 풍차·결정 룰렛 및 현재 선택된 테마의 3단계 악장 백그라운드 예열
            prewarmThemeStages(BgmTheme.WINDMILL_WALTZ)
        }
    }

    private fun getOrSynthBgmPcm(key: BgmTrackKey): ShortArray {
        return bgmPcmMap.computeIfAbsent(key) { k ->
            AcousticBgmSynthesizer.synthesizeTrackPcm(k.theme, k.mood)
        }
    }

    private fun prewarmThemeStages(theme: BgmTheme) {
        scope.launch {
            BgmMoodStage.entries.forEach { stage ->
                getOrSynthBgmPcm(BgmTrackKey(theme, stage))
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

        requestBgmTransition()
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

    /**
     * 화면 전환 시 테마 설정 및 [평온한 시작(`CALM_INTRO`)] 악장으로 초기화 + 해당 테마의 진행·기쁨 악장 백그라운드 예열
     */
    fun setBgmForScreen(gameType: GolfGameType?) {
        val nextTheme = when (gameType) {
            null -> BgmTheme.HOME_MEADOW
            GolfGameType.TEE_BOUNCE,
            GolfGameType.NOTCH_STICK,
            GolfGameType.FOREST_LADDER -> BgmTheme.TEE_ORDER_BREEZE
            GolfGameType.NEAR_PIN_PUTTING,
            GolfGameType.BALL_BASKET,
            GolfGameType.DOUBLE_DICE -> BgmTheme.GREEN_TENSION_CAPER
            GolfGameType.DECISION_ROULETTE_PLUS,
            GolfGameType.WINDMILL_ROULETTE,
            GolfGameType.LUCKY_WOOD_TILE,
            GolfGameType.YUT_THROW -> BgmTheme.WINDMILL_WALTZ
            GolfGameType.TAROT_FORTUNE -> BgmTheme.MYSTIC_TAROT_CABIN
        }
        desiredBgmTheme = nextTheme
        desiredBgmMood = BgmMoodStage.CALM_INTRO
        _activeThemeState.value = nextTheme
        _bgmMoodState.value = BgmMoodStage.CALM_INTRO
        isClimaxDucked = false

        prewarmThemeStages(nextTheme)
        requestBgmTransition()
    }

    /**
     * 게임 단계 변화(대기 -> 진행 중 두근거림 -> 결과 발표의 기쁨)에 맞춰 서사형 배경음악 악장을 부드럽게 크로스페이드 전환
     */
    fun setBgmMood(mood: BgmMoodStage) {
        if (desiredBgmMood == mood) return
        desiredBgmMood = mood
        _bgmMoodState.value = mood
        requestBgmTransition()
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
        requestBgmTransition()
    }

    fun onAppBackground() {
        isAppInForeground = false
        scope.launch {
            bgmMutex.withLock {
                cleanupFadingTracksLocked()
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
        // 슬로우모션 클라이맥스 시에도 음악의 두근거림이 살아 있도록 0.62배 밸런스 덕킹 적용
        val duckMultiplier = if (isClimaxDucked) 0.62f else 1.0f
        return (bgmVolume * 0.66f * duckMultiplier).coerceIn(0f, 1f)
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

    private fun requestBgmTransition() {
        transitionJob?.cancel()
        transitionJob = scope.launch {
            val targetKey = BgmTrackKey(desiredBgmTheme, desiredBgmMood)
            val pcm = getOrSynthBgmPcm(targetKey)
            crossfadeToTrack(targetKey, pcm)
        }
    }

    /**
     * 450ms 10단계 소프트 볼륨 크로스페이드로 끊김이나 클릭음 없이 악장 간 감정선을 연결
     */
    private suspend fun crossfadeToTrack(targetKey: BgmTrackKey, pcm: ShortArray) {
        var outgoingTrack: AudioTrack? = null
        var incomingTrack: AudioTrack? = null
        var startGain = 0f

        bgmMutex.withLock {
            cleanupFadingTracksLocked()
            val gain = computeEffectiveBgmGain()
            startGain = gain

            if (activeBgmKey == targetKey && activeBgmTrack != null) {
                try {
                    val existing = activeBgmTrack!!
                    if (existing.state == AudioTrack.STATE_INITIALIZED) {
                        existing.setVolume(gain)
                        if (gain <= 0.0001f || !isAppInForeground) {
                            if (existing.playState == AudioTrack.PLAYSTATE_PLAYING) {
                                existing.pause()
                            }
                            return@withLock
                        } else {
                            if (existing.playState != AudioTrack.PLAYSTATE_PLAYING) {
                                existing.setLoopPoints(0, pcm.size, -1)
                                existing.play()
                            }
                            if (existing.playState == AudioTrack.PLAYSTATE_PLAYING) {
                                return@withLock
                            }
                        }
                    }
                } catch (_: Throwable) {
                }
            }

            if (!isAppInForeground) {
                releaseBgmTrackLocked()
                return@withLock
            }

            val created = buildStaticLoopTrack(pcm)
            if (created == null) {
                releaseBgmTrackLocked()
                return@withLock
            }

            outgoingTrack = activeBgmTrack
            if (outgoingTrack != null) {
                fadingOutTracks.add(outgoingTrack)
            }

            activeBgmTrack = created
            activeBgmKey = targetKey
            incomingTrack = created

            try {
                if (gain > 0.0001f) {
                    created.setVolume(if (outgoingTrack != null) 0.02f * gain else gain)
                    created.play()
                } else {
                    created.setVolume(0f)
                }
            } catch (_: Throwable) {
            }
        }

        val inTrack = incomingTrack ?: return
        val outTrack = outgoingTrack

        if (outTrack != null && startGain > 0.0001f && isAppInForeground) {
            val steps = 10
            for (step in 1..steps) {
                delay(45L)
                val progress = step.toFloat() / steps.toFloat()
                val currentTargetGain = computeEffectiveBgmGain()
                bgmMutex.withLock {
                    if (activeBgmTrack === inTrack) {
                        runCatching { inTrack.setVolume((currentTargetGain * progress).coerceIn(0f, 1f)) }
                    }
                    if (fadingOutTracks.contains(outTrack)) {
                        runCatching { outTrack.setVolume((currentTargetGain * (1f - progress)).coerceIn(0f, 1f)) }
                    }
                }
            }
        }

        if (outTrack != null) {
            bgmMutex.withLock {
                fadingOutTracks.remove(outTrack)
                safeStopAndRelease(outTrack)
                if (activeBgmTrack === inTrack) {
                    val finalGain = computeEffectiveBgmGain()
                    runCatching {
                        inTrack.setVolume(finalGain)
                        if (finalGain <= 0.0001f && inTrack.playState == AudioTrack.PLAYSTATE_PLAYING) {
                            inTrack.pause()
                        }
                    }
                }
            }
        }
    }

    private fun buildStaticLoopTrack(pcm: ShortArray): AudioTrack? {
        return try {
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
            track
        } catch (_: Throwable) {
            null
        }
    }

    private fun cleanupFadingTracksLocked() {
        if (fadingOutTracks.isEmpty()) return
        val copy = fadingOutTracks.toList()
        fadingOutTracks.clear()
        copy.forEach { safeStopAndRelease(it) }
    }

    private fun releaseBgmTrackLocked() {
        cleanupFadingTracksLocked()
        val old = activeBgmTrack
        activeBgmTrack = null
        activeBgmKey = null
        if (old != null) {
            safeStopAndRelease(old)
        }
    }

    private fun safeStopAndRelease(track: AudioTrack) {
        try {
            track.stop()
        } catch (_: Throwable) {
        }
        try {
            track.release()
        } catch (_: Throwable) {
        }
    }

    // --- 게임 시작 · 진행 · 클라이맥스 · 결과 효과음 호출 API ---

    fun playGameStart(gameType: GolfGameType) {
        if (hapticEnabled) vibrateSingle(24, 150)
        val pcm = if (gameType.category == GameCategory.TEE_ORDER) gameStartOrderPcm else gameStartPenaltyPcm
        playBuffer(pcm, SoundChannel.GAME_SFX, baseVolume = 0.84f)
    }

    fun playTeeTossStart() {
        if (hapticEnabled) vibrateSingle(16, 120)
        playBuffer(teeTossStartPcm, SoundChannel.GAME_SFX, baseVolume = 0.78f)
    }

    fun playTeeBounce() {
        if (hapticEnabled) vibrateSingle(24, 190)
        playBuffer(teeBouncePcm, SoundChannel.GAME_SFX, baseVolume = 0.85f)
    }

    fun playSpinWhooshTick(highPitch: Boolean = false) {
        if (hapticEnabled) vibrateSingle(8, 65)
        playBuffer(
            if (highPitch) spinWhooshHighPcm else spinWhooshLowPcm,
            SoundChannel.GAME_SFX,
            baseVolume = 0.62f
        )
    }

    fun playGrassLand() {
        if (hapticEnabled) vibrateSingle(14, 90)
        playBuffer(grassLandPcm, SoundChannel.GAME_SFX, baseVolume = 0.68f)
    }

    fun playStickCaseShuffle() {
        if (hapticEnabled) vibratePattern(longArrayOf(0, 18, 45, 18, 45, 22), intArrayOf(0, 120, 0, 150, 0, 130))
        playBuffer(stickCaseShufflePcm, SoundChannel.GAME_SFX, baseVolume = 0.80f)
    }

    fun playStickSlide(orderIndex: Int) {
        if (hapticEnabled) vibrateSingle(18, 130)
        val note = stickSlideNotes[orderIndex.coerceIn(0, stickSlideNotes.lastIndex)]
        playBuffer(note, SoundChannel.GAME_SFX, baseVolume = 0.82f)
    }

    fun playNotchRevealTick(stepIndex: Int) {
        if (hapticEnabled) vibrateSingle(14, 110)
        val bell = notchRevealNotes[stepIndex.coerceIn(0, notchRevealNotes.lastIndex)]
        playBuffer(bell, SoundChannel.GAME_SFX, baseVolume = 0.80f)
    }

    fun playLadderStartWhistle() {
        if (hapticEnabled) vibrateSingle(22, 145)
        playBuffer(ladderWhistlePcm, SoundChannel.GAME_SFX, baseVolume = 0.80f)
    }

    fun playWoodTick(highPitch: Boolean = false) {
        if (hapticEnabled) vibrateSingle(10, 80)
        playBuffer(
            if (highPitch) woodTickHighPcm else woodTickLowPcm,
            SoundChannel.GAME_SFX,
            baseVolume = 0.68f
        )
    }

    fun playPuttStroke() {
        if (hapticEnabled) vibrateSingle(22, 175)
        playBuffer(puttStrokePcm, SoundChannel.GAME_SFX, baseVolume = 0.85f)
    }

    fun playBallRollTick() {
        playBuffer(ballRollTickPcm, SoundChannel.GAME_SFX, baseVolume = 0.45f)
    }

    fun playWaterSplash() {
        if (hapticEnabled) vibratePattern(longArrayOf(0, 35, 45, 55), intArrayOf(0, 200, 0, 140))
        playBuffer(waterSplashPcm, SoundChannel.GAME_SFX, baseVolume = 0.88f)
    }

    fun playCupIn() {
        if (hapticEnabled) vibratePattern(longArrayOf(0, 20, 40, 30), intArrayOf(0, 180, 0, 220))
        playBuffer(cupInPcm, SoundChannel.GAME_SFX, baseVolume = 0.88f)
    }

    fun playBasketShuffle() {
        if (hapticEnabled) vibrateSingle(20, 120)
        playBuffer(basketShufflePcm, SoundChannel.GAME_SFX, baseVolume = 0.78f)
    }

    fun playBallInspectPop() {
        if (hapticEnabled) vibrateSingle(15, 115)
        playBuffer(ballInspectPopPcm, SoundChannel.GAME_SFX, baseVolume = 0.78f)
    }

    fun playWindmillStart() {
        if (hapticEnabled) vibrateSingle(22, 145)
        playBuffer(windmillStartPcm, SoundChannel.GAME_SFX, baseVolume = 0.82f)
    }

    fun playRoulettePegTick(highPitch: Boolean = false) {
        if (hapticEnabled) vibrateSingle(9, 85)
        playBuffer(
            if (highPitch) roulettePegHighPcm else roulettePegLowPcm,
            SoundChannel.GAME_SFX,
            baseVolume = 0.70f
        )
    }

    fun playTileShuffle() {
        if (hapticEnabled) vibratePattern(longArrayOf(0, 15, 40, 15, 40, 18), intArrayOf(0, 115, 0, 140, 0, 120))
        playBuffer(tileShufflePcm, SoundChannel.GAME_SFX, baseVolume = 0.80f)
    }

    fun playCardFlip() {
        if (hapticEnabled) vibrateSingle(15, 110)
        playBuffer(cardFlipPcm, SoundChannel.GAME_SFX, baseVolume = 0.76f)
    }

    fun playTileTurnWhoosh() {
        if (hapticEnabled) vibrateSingle(18, 125)
        playBuffer(tileTurnWhooshPcm, SoundChannel.GAME_SFX, baseVolume = 0.78f)
    }

    fun playDiceCupShake() {
        if (hapticEnabled) vibratePattern(longArrayOf(0, 14, 34, 14, 34, 18), intArrayOf(0, 125, 0, 155, 0, 135))
        playBuffer(diceCupShakePcm, SoundChannel.GAME_SFX, baseVolume = 0.80f)
    }

    fun playDiceBounce(highPitch: Boolean = false) {
        if (hapticEnabled) vibrateSingle(12, 105)
        playBuffer(
            if (highPitch) diceBounceHighPcm else diceBounceLowPcm,
            SoundChannel.GAME_SFX,
            baseVolume = 0.76f
        )
    }

    fun playYutTossWhoosh() {
        if (hapticEnabled) vibrateSingle(20, 140)
        playBuffer(windmillStartPcm, SoundChannel.GAME_SFX, baseVolume = 0.82f)
    }

    fun playYutSticksClack(isBonusYutMo: Boolean = false) {
        if (hapticEnabled) {
            vibratePattern(
                if (isBonusYutMo) longArrayOf(0, 24, 35, 32, 40, 48) else longArrayOf(0, 20, 32, 24),
                if (isBonusYutMo) intArrayOf(0, 190, 0, 220, 0, 250) else intArrayOf(0, 165, 0, 195)
            )
        }
        playBuffer(yutSticksClackPcm, SoundChannel.GAME_SFX, baseVolume = 0.86f)
        if (isBonusYutMo) {
            playBuffer(yutBonusCheerPcm, SoundChannel.GAME_SFX, baseVolume = 0.84f)
        }
    }

    fun playTarotDeckSpread() {
        if (hapticEnabled) vibratePattern(longArrayOf(0, 10, 25, 10, 25, 12), intArrayOf(0, 90, 0, 110, 0, 125))
        playBuffer(tarotSpreadPcm, SoundChannel.GAME_SFX, baseVolume = 0.80f)
    }

    fun playTarotMysticReveal(highFortune: Boolean = true) {
        if (hapticEnabled) vibrateSingle(24, if (highFortune) 180 else 130)
        playBuffer(
            if (highFortune) tarotChimeHighPcm else tarotChimeCalmPcm,
            SoundChannel.GAME_SFX,
            baseVolume = 0.84f
        )
    }

    fun playHeartbeat(intense: Boolean = false) {
        if (hapticEnabled) {
            vibratePattern(
                if (intense) longArrayOf(0, 42, 95, 65) else longArrayOf(0, 28, 110, 42),
                if (intense) intArrayOf(0, 195, 0, 255) else intArrayOf(0, 125, 0, 180)
            )
        }
        val pcm = if (intense) heartbeatIntensePcm else heartbeatNormalPcm
        playBuffer(pcm, SoundChannel.HEARTBEAT, baseVolume = if (intense) 0.96f else 0.78f)
    }

    fun playSlowMoEnter() {
        if (hapticEnabled) vibrateSingle(45, 165)
        playBuffer(slowMoEnterPcm, SoundChannel.HEARTBEAT, baseVolume = 0.84f)
    }

    fun playGhibliFanfare() {
        if (hapticEnabled) {
            vibratePattern(longArrayOf(0, 25, 60, 30, 60, 65), intArrayOf(0, 150, 0, 190, 0, 245))
        }
        playBuffer(ghibliFanfarePcm, SoundChannel.RESULT_FANFARE, baseVolume = 0.92f)
    }

    fun playPenaltyReveal() {
        if (hapticEnabled) {
            vibratePattern(longArrayOf(0, 40, 70, 40, 70, 95), intArrayOf(0, 185, 0, 200, 0, 255))
        }
        playBuffer(penaltyRevealPcm, SoundChannel.RESULT_FANFARE, baseVolume = 0.92f)
    }

    fun playRouletteSpecialTwist() {
        if (hapticEnabled) {
            vibratePattern(longArrayOf(0, 22, 40, 32, 40, 45), intArrayOf(0, 175, 0, 210, 0, 240))
        }
        playBuffer(rouletteTwistSweepPcm, SoundChannel.GAME_SFX, baseVolume = 0.88f)
    }

    fun playRouletteDecisionSound(sound: RouletteDecisionSound) {
        val concrete = if (sound == RouletteDecisionSound.RANDOM) {
            val pool = RouletteDecisionSound.entries.filter { it != RouletteDecisionSound.RANDOM }
            pool[Random.nextInt(pool.size)]
        } else {
            sound
        }

        when (concrete) {
            RouletteDecisionSound.FANFARE -> playGhibliFanfare()
            RouletteDecisionSound.CHIME_BELL -> {
                if (hapticEnabled) vibratePattern(longArrayOf(0, 20, 45, 30), intArrayOf(0, 160, 0, 220))
                playBuffer(tarotChimeHighPcm, SoundChannel.RESULT_FANFARE, baseVolume = 0.90f)
            }
            RouletteDecisionSound.DRUM_ROLL_HIT -> {
                if (hapticEnabled) vibratePattern(longArrayOf(0, 30, 45, 55, 50, 80), intArrayOf(0, 180, 0, 210, 0, 255))
                playBuffer(rouletteDrumHitPcm, SoundChannel.RESULT_FANFARE, baseVolume = 0.92f)
            }
            RouletteDecisionSound.WOOD_CLAPPER -> {
                if (hapticEnabled) vibratePattern(longArrayOf(0, 24, 55, 36), intArrayOf(0, 220, 0, 255))
                playBuffer(rouletteWoodClapperPcm, SoundChannel.RESULT_FANFARE, baseVolume = 0.90f)
            }
            RouletteDecisionSound.COMIC_STAB -> playPenaltyReveal()
            RouletteDecisionSound.HEARTBEAT_CHIME -> {
                playHeartbeat(intense = true)
                playBuffer(yutBonusCheerPcm, SoundChannel.RESULT_FANFARE, baseVolume = 0.88f)
            }
            RouletteDecisionSound.RANDOM -> playGhibliFanfare()
        }
    }

    fun release() {
        unregisterSystemAudioObservers()
        transitionJob?.cancel()
        fadingOutTracks.toList().forEach { safeStopAndRelease(it) }
        fadingOutTracks.clear()
        val old = activeBgmTrack
        activeBgmTrack = null
        activeBgmKey = null
        if (old != null) {
            safeStopAndRelease(old)
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
                if (track != null) {
                    safeStopAndRelease(track)
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

    // --- 개별 효과음 합성기 (고음 자극을 낮춘 따뜻한 우드·로즈우드 공명 튜닝) ---

    private fun synthGameCurtainStart(brightMajor: Boolean): ShortArray {
        val duration = 0.78
        val total = (sfxSampleRate * duration).toInt()
        val out = ShortArray(total)
        val notes = if (brightMajor) {
            listOf(329.63, 392.00, 440.00, 523.25, 659.25) // E4 G4 A4 C5 E5
        } else {
            listOf(293.66, 349.23, 440.00, 523.25, 587.33) // D4 F4 A4 C5 D5
        }
        for (i in 0 until total) {
            val t = i.toDouble() / sfxSampleRate
            var acc = 0.0
            notes.forEachIndexed { idx, freq ->
                val onset = idx * 0.072
                if (t >= onset) {
                    val dt = t - onset
                    val env = (1.0 - exp(-dt * 95.0)) * exp(-dt * 4.4)
                    acc += (sin(2.0 * PI * freq * dt) * 0.78 + sin(2.0 * PI * freq * 2.0 * dt) * 0.16) * env * 0.25
                }
            }
            out[i] = (acc.coerceIn(-0.92, 0.92) * Short.MAX_VALUE).toInt().toShort()
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
            lpNoise = lpNoise * 0.90 + rng.nextDouble(-1.0, 1.0) * 0.10
            val tone = sin(2.0 * PI * phase) * 0.42 + lpNoise * 0.58
            out[i] = ((tone * env * 0.74).coerceIn(-0.90, 0.90) * Short.MAX_VALUE).toInt().toShort()
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
            val wave = sin(2.0 * PI * freq * t) * 0.80 + sin(2.0 * PI * (freq * 1.48) * t) * 0.18
            out[i] = ((wave * env * 0.72).coerceIn(-0.90, 0.90) * Short.MAX_VALUE).toInt().toShort()
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
                    val f = baseFreq + (k % 3) * 65.0
                    val env = exp(-dt * 55.0)
                    sum += (sin(2.0 * PI * f * dt) * 0.76 + sin(2.0 * PI * f * 1.52 * dt) * 0.22) * env * 0.40
                }
            }
            out[i] = (sum.coerceIn(-0.92, 0.92) * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }

    private fun synthCelestaBell(freq: Double, duration: Double): ShortArray {
        val total = (sfxSampleRate * duration).toInt()
        val out = ShortArray(total)
        for (i in 0 until total) {
            val t = i.toDouble() / sfxSampleRate
            val env = (1.0 - exp(-t * 110.0)) * exp(-t * 8.2)
            // 자극적인 3배음 대신 부드러운 2배음 우드 하프 톤 사용
            val wave = sin(2.0 * PI * freq * t) * 0.80 + sin(2.0 * PI * freq * 2.0 * t) * 0.16 * exp(-t * 16.0)
            out[i] = ((wave * env * 0.80).coerceIn(-0.92, 0.92) * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }

    private fun synthOcarinaStartWhistle(): ShortArray {
        val duration = 0.44
        val total = (sfxSampleRate * duration).toInt()
        val out = ShortArray(total)
        for (i in 0 until total) {
            val t = i.toDouble() / sfxSampleRate
            val freq = if (t < 0.16) 440.00 else 587.33 // A4 -> D5 따뜻한 로우 오카리나
            val localT = if (t < 0.16) t else t - 0.16
            val segLen = if (t < 0.16) 0.16 else 0.28
            val env = (1.0 - exp(-localT * 75.0)) * exp(-localT * (2.5 / segLen))
            val wave = sin(2.0 * PI * freq * t) * 0.84 + sin(2.0 * PI * freq * 2.0 * t) * 0.12
            out[i] = ((wave * env * 0.70).coerceIn(-0.92, 0.92) * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }

    private fun synthGrassRollTick(): ShortArray {
        val duration = 0.045
        val total = (sfxSampleRate * duration).toInt()
        val out = ShortArray(total)
        val rng = Random(88)
        var lp = 0.0
        for (i in 0 until total) {
            val t = i.toDouble() / sfxSampleRate
            val env = exp(-t * 85.0)
            lp = lp * 0.78 + rng.nextDouble(-0.4, 0.4) * 0.22
            val v = (sin(2.0 * PI * 280.0 * t) * 0.48 + lp) * env
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
            val f = 310.0 + 240.0 * (t / duration)
            val env = (1.0 - exp(-t * 120.0)) * exp(-t * 26.0)
            val v = sin(2.0 * PI * f * t) * env * 0.80
            out[i] = (v.coerceIn(-0.92, 0.92) * Short.MAX_VALUE).toInt().toShort()
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
            val wood = sin(2.0 * PI * freq * t) * 0.76
            val warmOvertone = sin(2.0 * PI * (freq * 1.62) * t) * 0.22 * exp(-t * 85.0)
            out[i] = (((wood + warmOvertone) * env * 0.78).coerceIn(-0.92, 0.92) * Short.MAX_VALUE).toInt().toShort()
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
        val notes = listOf(659.25, 587.33, 523.25, 440.00, 349.23) // 따뜻한 중음 하강 쉬머
        for (i in 0 until total) {
            val t = i.toDouble() / sfxSampleRate
            var acc = 0.0
            notes.forEachIndexed { idx, freq ->
                val onset = idx * 0.068
                if (t >= onset) {
                    val dt = t - onset
                    val env = exp(-dt * 4.8) * (1.0 - exp(-dt * 80.0))
                    acc += (sin(2.0 * PI * freq * dt) * 0.80 + 0.18 * sin(2.0 * PI * freq * 2.0 * dt)) * env * 0.24
                }
            }
            out[i] = (acc.coerceIn(-0.92, 0.92) * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }

    private fun synthTitaniumTeeBounce(): ShortArray {
        val duration = 0.28
        val total = (sfxSampleRate * duration).toInt()
        val out = ShortArray(total)
        for (i in 0 until total) {
            val t = i.toDouble() / sfxSampleRate
            val envResonance = exp(-t * 24.0)
            val envWood = exp(-t * 40.0)
            // 고음 금속 파열음을 줄이고 따뜻한 퍼시몬 우드·티 타격 공명으로 조율
            val ping = sin(2.0 * PI * 880.0 * t) * 0.42 * envResonance +
                sin(2.0 * PI * 1174.66 * t) * 0.20 * envResonance +
                sin(2.0 * PI * 490.0 * t) * 0.46 * envWood
            out[i] = (ping.coerceIn(-0.92, 0.92) * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }

    private fun synthSoftGrassDrop(): ShortArray {
        val duration = 0.13
        val total = (sfxSampleRate * duration).toInt()
        val out = ShortArray(total)
        val rng = Random(42)
        var lp = 0.0
        for (i in 0 until total) {
            val t = i.toDouble() / sfxSampleRate
            val env = exp(-t * 36.0)
            val thud = sin(2.0 * PI * (180.0 - 65.0 * t) * t) * 0.58
            lp = lp * 0.80 + rng.nextDouble(-1.0, 1.0) * 0.20
            out[i] = (((thud + lp * 0.22) * env).coerceIn(-0.9, 0.9) * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }

    private fun synthMarimbaNote(freq: Double, duration: Double): ShortArray {
        val total = (sfxSampleRate * duration).toInt()
        val out = ShortArray(total)
        for (i in 0 until total) {
            val t = i.toDouble() / sfxSampleRate
            val env = (1.0 - exp(-t * 115.0)) * exp(-t * 10.5)
            val wave = sin(2.0 * PI * freq * t) * 0.78 + sin(2.0 * PI * freq * 2.0 * t) * 0.20 * exp(-t * 22.0)
            out[i] = ((wave * env * 0.82).coerceIn(-0.92, 0.92) * Short.MAX_VALUE).toInt().toShort()
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
            val wave = sin(2.0 * PI * freq * t) * 0.78 + sin(2.0 * PI * (freq * 1.46) * t) * 0.22
            out[i] = ((wave * env * 0.76).coerceIn(-0.92, 0.92) * Short.MAX_VALUE).toInt().toShort()
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
            val wave = sin(2.0 * PI * 420.0 * t) * 0.70 + sin(2.0 * PI * 780.0 * t) * 0.30
            out[i] = ((wave * env * 0.84).coerceIn(-0.92, 0.92) * Short.MAX_VALUE).toInt().toShort()
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
            lp = lp * 0.86 + noise * 0.14
            val env = sin(PI * (t / duration).coerceIn(0.0, 1.0)) * exp(-t * 4.2)
            val bloop = if (t < 0.18) sin(2.0 * PI * (240.0 + 340.0 * t) * t) * exp(-t * 16.0) * 0.48 else 0.0
            val v = (lp * 1.3 * env + bloop).coerceIn(-0.92, 0.92)
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
            val rattle1 = sin(2.0 * PI * 740.0 * t) * exp(-t * 22.0) * 0.50
            val rattle2 = if (t >= 0.07) {
                val dt = t - 0.07
                (sin(2.0 * PI * 987.77 * dt) * 0.52 + sin(2.0 * PI * 1174.66 * dt) * 0.24) * exp(-dt * 12.0)
            } else 0.0
            out[i] = ((rattle1 + rattle2).coerceIn(-0.92, 0.92) * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }

    private fun synthCardFlip(): ShortArray {
        val duration = 0.15
        val total = (sfxSampleRate * duration).toInt()
        val out = ShortArray(total)
        val rng = Random(101)
        var lp = 0.0
        for (i in 0 until total) {
            val t = i.toDouble() / sfxSampleRate
            lp = lp * 0.80 + rng.nextDouble(-1.0, 1.0) * 0.20
            val swoosh = lp * sin(PI * (t / duration)) * 0.28
            val snap = if (t > 0.05) sin(2.0 * PI * 520.0 * (t - 0.05)) * exp(-(t - 0.05) * 48.0) * 0.52 else 0.0
            out[i] = ((swoosh + snap).coerceIn(-0.9, 0.9) * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }

    // 따뜻한 메이저 9th 어쿠스틱 피아노·첼로·마림바 축하 팡파르 (고음 1.5kHz 제거, 포근한 중저음 화음 보강)
    private fun synthGhibliChordFanfare(): ShortArray {
        val duration = 1.95
        val total = (sfxSampleRate * duration).toInt()
        val out = ShortArray(total)
        val chord = listOf(261.63, 329.63, 392.00, 493.88, 587.33, 783.99) // C4 E4 G4 B4 D5 G5
        for (i in 0 until total) {
            val t = i.toDouble() / sfxSampleRate
            var sum = 0.0
            chord.forEachIndexed { idx, freq ->
                val onset = idx * 0.085
                if (t >= onset) {
                    val dt = t - onset
                    val attack = 1.0 - exp(-dt * 95.0)
                    val decay = exp(-dt * 2.1)
                    val tone = sin(2.0 * PI * freq * dt) * 0.74 +
                        sin(2.0 * PI * freq * 2.0 * dt) * 0.18 * exp(-dt * 6.5)
                    sum += tone * attack * decay * 0.23
                }
            }
            if (t >= 0.04) {
                val bt = t - 0.04
                sum += sin(2.0 * PI * 130.81 * bt) * (1.0 - exp(-bt * 45.0)) * exp(-bt * 1.8) * 0.22
            }
            out[i] = (sum.coerceIn(-0.94, 0.94) * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }

    private fun synthPlayfulPenaltyTune(): ShortArray {
        val duration = 1.75
        val total = (sfxSampleRate * duration).toInt()
        val out = ShortArray(total)
        val notes = listOf(
            Triple(0.00, 392.00, 0.16), // G4
            Triple(0.14, 440.00, 0.16), // A4
            Triple(0.28, 392.00, 0.16), // G4
            Triple(0.42, 329.63, 0.16), // E4
            Triple(0.58, 523.25, 0.95), // C5
            Triple(0.58, 659.25, 0.95), // E5
            Triple(0.58, 783.99, 0.95)  // G5
        )
        for (i in 0 until total) {
            val t = i.toDouble() / sfxSampleRate
            var sum = 0.0
            notes.forEach { (onset, freq, len) ->
                if (t >= onset && t <= onset + len + 0.30) {
                    val dt = t - onset
                    val env = (1.0 - exp(-dt * 105.0)) * exp(-dt * 3.6)
                    sum += (sin(2.0 * PI * freq * dt) * 0.80 + 0.18 * sin(2.0 * PI * freq * 2.0 * dt)) * env * 0.26
                }
            }
            out[i] = (sum.coerceIn(-0.94, 0.94) * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }

    private fun synthYutFourStickClack(): ShortArray {
        val duration = 0.28
        val total = (sfxSampleRate * duration).toInt()
        val out = ShortArray(total)
        val hits = listOf(
            0.00 to 510.0,
            0.045 to 610.0,
            0.095 to 550.0,
            0.135 to 670.0
        )
        for (i in 0 until total) {
            val t = i.toDouble() / sfxSampleRate
            var acc = 0.0
            hits.forEach { (onset, freq) ->
                if (t >= onset) {
                    val dt = t - onset
                    val env = exp(-dt * 54.0)
                    acc += (sin(2.0 * PI * freq * dt) * 0.72 + sin(2.0 * PI * (freq * 1.54) * dt) * 0.28) * env * 0.46
                }
            }
            out[i] = (acc.coerceIn(-0.92, 0.92) * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }

    private fun synthYutBonusBell(): ShortArray {
        val duration = 0.56
        val total = (sfxSampleRate * duration).toInt()
        val out = ShortArray(total)
        val notes = listOf(0.00 to 392.00, 0.08 to 493.88, 0.16 to 587.33, 0.24 to 783.99)
        for (i in 0 until total) {
            val t = i.toDouble() / sfxSampleRate
            var acc = 0.0
            notes.forEach { (onset, freq) ->
                if (t >= onset) {
                    val dt = t - onset
                    val env = (1.0 - exp(-dt * 100.0)) * exp(-dt * 7.2)
                    acc += (sin(2.0 * PI * freq * dt) * 0.80 + 0.18 * sin(2.0 * PI * freq * 2.0 * dt)) * env * 0.27
                }
            }
            out[i] = (acc.coerceIn(-0.92, 0.92) * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }

    private fun synthTarotSilkSpread(): ShortArray {
        val duration = 0.36
        val total = (sfxSampleRate * duration).toInt()
        val out = ShortArray(total)
        val rng = Random(2026)
        var lp = 0.0
        for (i in 0 until total) {
            val t = i.toDouble() / sfxSampleRate
            val n = rng.nextDouble(-1.0, 1.0)
            lp = lp * 0.82 + n * 0.18
            val env = sin(PI * (t / duration).coerceIn(0.0, 1.0))
            val warmHarp = sin(2.0 * PI * (440.0 + 330.0 * (t / duration)) * t) * 0.16 * env
            val tick = if (((t * 26.0).toInt() % 2) == 0) 1.0 else 0.55
            out[i] = (((lp * 0.50 * env * tick) + warmHarp).coerceIn(-0.90, 0.90) * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }

    private fun synthTarotCelestaArpeggio(bright: Boolean): ShortArray {
        val duration = 0.85
        val total = (sfxSampleRate * duration).toInt()
        val out = ShortArray(total)
        val tones = if (bright) {
            listOf(329.63, 392.00, 493.88, 659.25, 783.99) // E4 G4 B4 E5 G5 포근한 하프 코드
        } else {
            listOf(261.63, 329.63, 392.00, 493.88)         // C4 E4 G4 B4
        }
        for (i in 0 until total) {
            val t = i.toDouble() / sfxSampleRate
            var acc = 0.0
            tones.forEachIndexed { idx, freq ->
                val onset = idx * 0.075
                if (t >= onset) {
                    val dt = t - onset
                    val env = (1.0 - exp(-dt * 105.0)) * exp(-dt * 4.4)
                    acc += (sin(2.0 * PI * freq * dt) * 0.78 + sin(2.0 * PI * freq * 2.0 * dt) * 0.20) * env * 0.24
                }
            }
            out[i] = (acc.coerceIn(-0.92, 0.92) * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }

    private fun synthRouletteDrumRollHit(): ShortArray {
        val duration = 1.15
        val total = (sfxSampleRate * duration).toInt()
        val out = ShortArray(total)
        val rng = Random(202605)
        for (i in 0 until total) {
            val t = i.toDouble() / sfxSampleRate
            var acc = 0.0
            for (k in 0..7) {
                val onset = k * 0.062
                if (t >= onset && t < onset + 0.14) {
                    val dt = t - onset
                    val env = exp(-dt * 34.0)
                    val f = 135.0 - dt * 280.0
                    acc += (sin(2.0 * PI * f.coerceAtLeast(58.0) * dt) * 0.76 + rng.nextDouble(-0.22, 0.22)) * env * 0.42
                }
            }
            if (t >= 0.56) {
                val dt = t - 0.56
                val boomEnv = exp(-dt * 7.5)
                val boomFreq = (118.0 - dt * 65.0).coerceAtLeast(52.0)
                val chimeEnv = exp(-dt * 4.2)
                acc += sin(2.0 * PI * boomFreq * dt) * boomEnv * 0.65
                acc += (sin(2.0 * PI * 392.00 * dt) * 0.22 + sin(2.0 * PI * 523.25 * dt) * 0.18) * chimeEnv
            }
            out[i] = (acc.coerceIn(-0.94, 0.94) * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }

    private fun synthRouletteWoodClapper(): ShortArray {
        val duration = 0.85
        val total = (sfxSampleRate * duration).toInt()
        val out = ShortArray(total)
        val hits = listOf(0.00 to 690.0, 0.22 to 790.0)
        for (i in 0 until total) {
            val t = i.toDouble() / sfxSampleRate
            var acc = 0.0
            hits.forEachIndexed { idx, (onset, baseF) ->
                if (t >= onset) {
                    val dt = t - onset
                    val env = exp(-dt * (if (idx == 0) 32.0 else 18.0))
                    val wave = sin(2.0 * PI * baseF * dt) * 0.68 +
                        sin(2.0 * PI * (baseF * 1.62) * dt) * 0.24
                    acc += wave * env * 0.84
                }
            }
            out[i] = (acc.coerceIn(-0.94, 0.94) * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }

    private fun synthRouletteTwistSweep(): ShortArray {
        val duration = 0.62
        val total = (sfxSampleRate * duration).toInt()
        val out = ShortArray(total)
        var phase = 0.0
        for (i in 0 until total) {
            val t = i.toDouble() / sfxSampleRate
            val u = (t / duration).coerceIn(0.0, 1.0)
            val freq = 293.66 + sin(PI * u) * 330.0
            phase += freq / sfxSampleRate
            val env = sin(PI * u) * (1.0 - 0.25 * u)
            val v = (sin(2.0 * PI * phase) * 0.74 + sin(4.0 * PI * phase) * 0.18) * env * 0.74
            out[i] = (v.coerceIn(-0.92, 0.92) * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }
}
