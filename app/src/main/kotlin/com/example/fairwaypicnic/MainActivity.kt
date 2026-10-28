package com.example.fairwaypicnic

import android.graphics.Color
import android.media.AudioManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.fairwaypicnic.audio.GhibliSoundEngine
import com.example.fairwaypicnic.data.PlayerPreferencesRepository
import com.example.fairwaypicnic.model.AppSettings
import com.example.fairwaypicnic.model.GolfGameType
import com.example.fairwaypicnic.ui.components.PlayerEntryDialog
import com.example.fairwaypicnic.ui.components.SoundSettingsDialog
import com.example.fairwaypicnic.ui.games.ForestBallBasketGameScreen
import com.example.fairwaypicnic.ui.games.LadderGameScreen
import com.example.fairwaypicnic.ui.games.LuckyWoodTileGameScreen
import com.example.fairwaypicnic.ui.games.NearPinPuttingGameScreen
import com.example.fairwaypicnic.ui.games.NotchStickGameScreen
import com.example.fairwaypicnic.ui.games.TeeBounceGameScreen
import com.example.fairwaypicnic.ui.games.WindmillRouletteGameScreen
import com.example.fairwaypicnic.ui.home.HomeScreen
import com.example.fairwaypicnic.ui.theme.FairwayPicnicTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 휴대폰 하드웨어 볼륨 키가 언제나 미디어 마스터 볼륨(내장·외장·블루투스 스피커)을 즉각 제어하도록 연결
        volumeControlStream = AudioManager.STREAM_MUSIC

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        )

        setContent {
            FairwayPicnicTheme(darkTheme = false) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    FairwayPicnicApp()
                }
            }
        }
    }
}

@Composable
fun FairwayPicnicApp() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val repository = remember { PlayerPreferencesRepository(context) }
    val soundEngine = remember { GhibliSoundEngine(context) }

    var settings by remember { mutableStateOf(AppSettings()) }
    var activeGame by remember { mutableStateOf<GolfGameType?>(null) }

    // 게임 진입 전 공통 '선수입장' 게이트 대상 게임
    var pendingEntryGame by remember { mutableStateOf<GolfGameType?>(null) }
    // 로비 또는 게임 도중 '선수입장' 명단 변경 모달
    var showMidPlayerDialog by remember { mutableStateOf(false) }
    // 사운드 채널별 설정 및 시스템 볼륨 연동 모달
    var showSoundDialog by remember { mutableStateOf(false) }

    // 앱 진입 시 저장된 선수 이름·진행시간·사운드 설정 비동기 로드
    LaunchedEffect(Unit) {
        val loaded = repository.loadSettings()
        settings = loaded
        soundEngine.applySettings(loaded)
    }

    // 화면 전환 시 로비/각 게임 분위기에 어울리는 지브리 배경음악 테마 전환
    LaunchedEffect(activeGame) {
        soundEngine.setBgmForScreen(activeGame)
    }

    // 백그라운드 이동/화면 잠금 시 배경음악 자동 일시정지 및 복귀 시 재개
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START, Lifecycle.Event.ON_RESUME -> soundEngine.onAppForeground()
                Lifecycle.Event.ON_STOP -> soundEngine.onAppBackground()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            soundEngine.release()
        }
    }

    fun updateSettings(newSettings: AppSettings) {
        settings = newSettings
        soundEngine.applySettings(newSettings)
        scope.launch {
            repository.saveSettings(newSettings)
        }
    }

    BackHandler(enabled = activeGame != null) {
        soundEngine.setClimaxDucking(false)
        activeGame = null
    }

    Crossfade(
        targetState = activeGame,
        animationSpec = tween(durationMillis = 220),
        label = "screenTransition"
    ) { target ->
        when (target) {
            null -> HomeScreen(
                settings = settings,
                onSelectGame = { chosen ->
                    soundEngine.playWoodTick(highPitch = true)
                    // 모든 미니게임 시작 전 공통 '선수입장' 설정 단계 오픈
                    pendingEntryGame = chosen
                },
                onOpenPlayerDialog = { showMidPlayerDialog = true },
                onOpenSoundDialog = { showSoundDialog = true }
            )

            GolfGameType.TEE_BOUNCE -> TeeBounceGameScreen(
                settings = settings,
                soundEngine = soundEngine,
                onBack = {
                    soundEngine.setClimaxDucking(false)
                    activeGame = null
                },
                onOpenPlayerDialog = { showMidPlayerDialog = true },
                onOpenSoundDialog = { showSoundDialog = true }
            )

            GolfGameType.NOTCH_STICK -> NotchStickGameScreen(
                settings = settings,
                soundEngine = soundEngine,
                onBack = {
                    soundEngine.setClimaxDucking(false)
                    activeGame = null
                },
                onOpenPlayerDialog = { showMidPlayerDialog = true },
                onOpenSoundDialog = { showSoundDialog = true }
            )

            GolfGameType.FOREST_LADDER -> LadderGameScreen(
                settings = settings,
                soundEngine = soundEngine,
                onBack = {
                    soundEngine.setClimaxDucking(false)
                    activeGame = null
                },
                onOpenPlayerDialog = { showMidPlayerDialog = true },
                onOpenSoundDialog = { showSoundDialog = true }
            )

            GolfGameType.NEAR_PIN_PUTTING -> NearPinPuttingGameScreen(
                settings = settings,
                soundEngine = soundEngine,
                onBack = {
                    soundEngine.setClimaxDucking(false)
                    activeGame = null
                },
                onOpenPlayerDialog = { showMidPlayerDialog = true },
                onOpenSoundDialog = { showSoundDialog = true }
            )

            GolfGameType.BALL_BASKET -> ForestBallBasketGameScreen(
                settings = settings,
                soundEngine = soundEngine,
                onBack = {
                    soundEngine.setClimaxDucking(false)
                    activeGame = null
                },
                onOpenPlayerDialog = { showMidPlayerDialog = true },
                onOpenSoundDialog = { showSoundDialog = true }
            )

            GolfGameType.WINDMILL_ROULETTE -> WindmillRouletteGameScreen(
                settings = settings,
                soundEngine = soundEngine,
                onBack = {
                    soundEngine.setClimaxDucking(false)
                    activeGame = null
                },
                onOpenPlayerDialog = { showMidPlayerDialog = true },
                onOpenSoundDialog = { showSoundDialog = true }
            )

            GolfGameType.LUCKY_WOOD_TILE -> LuckyWoodTileGameScreen(
                settings = settings,
                soundEngine = soundEngine,
                onBack = {
                    soundEngine.setClimaxDucking(false)
                    activeGame = null
                },
                onOpenPlayerDialog = { showMidPlayerDialog = true },
                onOpenSoundDialog = { showSoundDialog = true }
            )
        }
    }

    // 게임 시작 직전 공통 '선수입장' 모달 (이전 게임 설정 그대로 유지 또는 즉시 수정 후 입장)
    if (pendingEntryGame != null) {
        val targetGame = pendingEntryGame!!
        PlayerEntryDialog(
            targetGame = targetGame,
            isBeforeGameStart = true,
            settings = settings,
            soundEngine = soundEngine,
            onConfirm = { updated ->
                updateSettings(updated)
                pendingEntryGame = null
                activeGame = targetGame
                soundEngine.playGameStart(targetGame)
            },
            onDismiss = {
                pendingEntryGame = null
            }
        )
    }

    // 로비 또는 게임 중 언제든 열 수 있는 '선수입장' 편집 모달
    if (showMidPlayerDialog) {
        PlayerEntryDialog(
            targetGame = activeGame,
            isBeforeGameStart = false,
            settings = settings,
            soundEngine = soundEngine,
            onConfirm = { updated ->
                updateSettings(updated)
                showMidPlayerDialog = false
            },
            onDismiss = {
                showMidPlayerDialog = false
            }
        )
    }

    // 사운드 채널별 ON/OFF · 볼륨 및 휴대폰 시스템 볼륨 연동 설정 모달
    if (showSoundDialog) {
        SoundSettingsDialog(
            settings = settings,
            soundEngine = soundEngine,
            onUpdateSettings = { updated ->
                updateSettings(updated)
            },
            onDismiss = {
                showSoundDialog = false
            }
        )
    }
}
