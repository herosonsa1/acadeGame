package com.jjalddalmark.minigamerullet

import android.app.Activity
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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.jjalddalmark.minigamerullet.ads.AdMobCoordinator
import com.jjalddalmark.minigamerullet.ads.ExitActionTarget
import com.jjalddalmark.minigamerullet.ads.ExitCountdownOverlay
import com.jjalddalmark.minigamerullet.audio.GhibliSoundEngine
import com.jjalddalmark.minigamerullet.audio.LocalSoundEngine
import com.jjalddalmark.minigamerullet.data.PlayerPreferencesRepository
import com.jjalddalmark.minigamerullet.model.AppSettings
import com.jjalddalmark.minigamerullet.model.GolfGameType
import com.jjalddalmark.minigamerullet.ui.components.PlayerEntryDialog
import com.jjalddalmark.minigamerullet.ui.components.SoundSettingsDialog
import com.jjalddalmark.minigamerullet.ui.games.DecisionRouletteGameScreen
import com.jjalddalmark.minigamerullet.ui.games.DoubleDiceGameScreen
import com.jjalddalmark.minigamerullet.ui.games.ForestBallBasketGameScreen
import com.jjalddalmark.minigamerullet.ui.games.LadderGameScreen
import com.jjalddalmark.minigamerullet.ui.games.LuckyWoodTileGameScreen
import com.jjalddalmark.minigamerullet.ui.games.NearPinPuttingGameScreen
import com.jjalddalmark.minigamerullet.ui.games.NotchStickGameScreen
import com.jjalddalmark.minigamerullet.ui.games.TarotFortuneGameScreen
import com.jjalddalmark.minigamerullet.ui.games.TeeBounceGameScreen
import com.jjalddalmark.minigamerullet.ui.games.WindmillRouletteGameScreen
import com.jjalddalmark.minigamerullet.ui.games.YutNoriGameScreen
import com.jjalddalmark.minigamerullet.ui.home.HomeScreen
import com.jjalddalmark.minigamerullet.ui.theme.FairwayPicnicTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 백그라운드 스레드(Dispatchers.IO)에서 Google AdMob 비동기 초기화 및 종료·최소화 전면 광고 사전 로드
        AdMobCoordinator.initializeAsync(applicationContext)

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
    val activity = context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val repository = remember { PlayerPreferencesRepository(context) }
    val soundEngine = remember { GhibliSoundEngine(context) }

    var settings by remember { mutableStateOf(AppSettings()) }
    var activeGame by rememberSaveable { mutableStateOf<GolfGameType?>(null) }

    // 게임 진입 전 공통 '선수입장' 게이트 대상 게임
    var pendingEntryGame by rememberSaveable { mutableStateOf<GolfGameType?>(null) }
    // 게임 입장 직후 지브리 인트로 동영상 컷신을 재생할 대상 게임
    var introCutsceneGame by rememberSaveable { mutableStateOf<GolfGameType?>(null) }
    // 로비 또는 게임 도중 '선수입장' 명단 변경 모달
    var showMidPlayerDialog by rememberSaveable { mutableStateOf(false) }
    // 사운드 채널별 설정 및 시스템 볼륨 연동 모달
    var showSoundDialog by rememberSaveable { mutableStateOf(false) }
    // 앱 종료 · 최소화 전면 광고 직후 표시할 카운트다운 오버레이 대상
    var exitCountdownTarget by rememberSaveable { mutableStateOf<ExitActionTarget?>(null) }
    var isShowingExitAd by remember { mutableStateOf(false) }

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

    // 배경음(BGM)만 음소거/해제하는 원터치 토글 핸들러 (효과음은 그대로 유지)
    fun handleToggleBgmMute() {
        val next = settings.toggleBgmOnlyMute()
        updateSettings(next)
        soundEngine.playWoodTick(highPitch = !next.isBgmMuted)
    }

    fun executeExitOrMinimize(target: ExitActionTarget) {
        exitCountdownTarget = null
        isShowingExitAd = false
        when (target) {
            ExitActionTarget.EXIT_APP -> activity?.finish()
            ExitActionTarget.MINIMIZE_APP -> {
                runCatching { activity?.moveTaskToBack(true) }
            }
        }
    }

    fun triggerGracefulExitOrMinimize(target: ExitActionTarget) {
        if (!isShowingExitAd && exitCountdownTarget == null) {
            val hostActivity = activity
            if (hostActivity == null) {
                exitCountdownTarget = target
            } else {
                isShowingExitAd = true
                AdMobCoordinator.showExitInterstitialThen(
                    activity = hostActivity,
                    onAdShown = { soundEngine.onAppBackground() },
                    onComplete = {
                        isShowingExitAd = false
                        soundEngine.onAppForeground()
                        exitCountdownTarget = target
                    }
                )
            }
        }
    }

    BackHandler(enabled = true) {
        val currentExitTarget = exitCountdownTarget
        when {
            currentExitTarget != null -> executeExitOrMinimize(currentExitTarget)
            isShowingExitAd -> Unit
            activeGame != null -> {
                soundEngine.setClimaxDucking(false)
                introCutsceneGame = null
                activeGame = null
            }
            else -> triggerGracefulExitOrMinimize(ExitActionTarget.EXIT_APP)
        }
    }

    CompositionLocalProvider(LocalSoundEngine provides soundEngine) {
    Box(modifier = Modifier.fillMaxSize()) {
    Crossfade(
        targetState = activeGame,
        animationSpec = tween(durationMillis = 220),
        label = "screenTransition"
    ) { target ->
        when (target) {
            null -> HomeScreen(
                settings = settings,
                onSelectGame = { chosen ->
                    if (chosen == GolfGameType.DECISION_ROULETTE_PLUS) {
                        // 만능 결정 룰렛+는 골프 선수입장 게이트 없이 곧바로 실행
                        pendingEntryGame = null
                        introCutsceneGame = null
                        activeGame = chosen
                        soundEngine.playGameStart(chosen)
                    } else {
                        soundEngine.playWoodTick(highPitch = true)
                        val adjusted = settings.ensureMinPlayersForGame(chosen)
                        if (adjusted != settings) {
                            updateSettings(adjusted)
                        }
                        // 골프 미니게임 시작 전 공통 '선수입장' 설정 단계 오픈
                        pendingEntryGame = chosen
                    }
                },
                onToggleBgmMute = ::handleToggleBgmMute,
                onOpenPlayerDialog = { showMidPlayerDialog = true },
                onOpenSoundDialog = { showSoundDialog = true },
                onMinimizeApp = {
                    triggerGracefulExitOrMinimize(ExitActionTarget.MINIMIZE_APP)
                }
            )

            GolfGameType.DECISION_ROULETTE_PLUS -> DecisionRouletteGameScreen(
                settings = settings,
                soundEngine = soundEngine,
                onToggleBgmMute = ::handleToggleBgmMute,
                onBack = {
                    soundEngine.setClimaxDucking(false)
                    introCutsceneGame = null
                    activeGame = null
                },
                onOpenSoundDialog = { showSoundDialog = true }
            )

            GolfGameType.TEE_BOUNCE -> TeeBounceGameScreen(
                settings = settings,
                soundEngine = soundEngine,
                showIntroCutscene = introCutsceneGame == target,
                onIntroCutsceneFinished = { if (introCutsceneGame == target) introCutsceneGame = null },
                onToggleBgmMute = ::handleToggleBgmMute,
                onBack = {
                    soundEngine.setClimaxDucking(false)
                    introCutsceneGame = null
                    activeGame = null
                },
                onOpenPlayerDialog = { showMidPlayerDialog = true },
                onOpenSoundDialog = { showSoundDialog = true }
            )

            GolfGameType.NOTCH_STICK -> NotchStickGameScreen(
                settings = settings,
                soundEngine = soundEngine,
                showIntroCutscene = introCutsceneGame == target,
                onIntroCutsceneFinished = { if (introCutsceneGame == target) introCutsceneGame = null },
                onToggleBgmMute = ::handleToggleBgmMute,
                onBack = {
                    soundEngine.setClimaxDucking(false)
                    introCutsceneGame = null
                    activeGame = null
                },
                onOpenPlayerDialog = { showMidPlayerDialog = true },
                onOpenSoundDialog = { showSoundDialog = true }
            )

            GolfGameType.FOREST_LADDER -> LadderGameScreen(
                settings = settings,
                soundEngine = soundEngine,
                showIntroCutscene = introCutsceneGame == target,
                onIntroCutsceneFinished = { if (introCutsceneGame == target) introCutsceneGame = null },
                onToggleBgmMute = ::handleToggleBgmMute,
                onBack = {
                    soundEngine.setClimaxDucking(false)
                    introCutsceneGame = null
                    activeGame = null
                },
                onOpenPlayerDialog = { showMidPlayerDialog = true },
                onOpenSoundDialog = { showSoundDialog = true }
            )

            GolfGameType.NEAR_PIN_PUTTING -> NearPinPuttingGameScreen(
                settings = settings,
                soundEngine = soundEngine,
                showIntroCutscene = introCutsceneGame == target,
                onIntroCutsceneFinished = { if (introCutsceneGame == target) introCutsceneGame = null },
                onToggleBgmMute = ::handleToggleBgmMute,
                onBack = {
                    soundEngine.setClimaxDucking(false)
                    introCutsceneGame = null
                    activeGame = null
                },
                onOpenPlayerDialog = { showMidPlayerDialog = true },
                onOpenSoundDialog = { showSoundDialog = true }
            )

            GolfGameType.BALL_BASKET -> ForestBallBasketGameScreen(
                settings = settings,
                soundEngine = soundEngine,
                showIntroCutscene = introCutsceneGame == target,
                onIntroCutsceneFinished = { if (introCutsceneGame == target) introCutsceneGame = null },
                onToggleBgmMute = ::handleToggleBgmMute,
                onBack = {
                    soundEngine.setClimaxDucking(false)
                    introCutsceneGame = null
                    activeGame = null
                },
                onOpenPlayerDialog = { showMidPlayerDialog = true },
                onOpenSoundDialog = { showSoundDialog = true }
            )

            GolfGameType.WINDMILL_ROULETTE -> WindmillRouletteGameScreen(
                settings = settings,
                soundEngine = soundEngine,
                showIntroCutscene = introCutsceneGame == target,
                onIntroCutsceneFinished = { if (introCutsceneGame == target) introCutsceneGame = null },
                onToggleBgmMute = ::handleToggleBgmMute,
                onBack = {
                    soundEngine.setClimaxDucking(false)
                    introCutsceneGame = null
                    activeGame = null
                },
                onOpenPlayerDialog = { showMidPlayerDialog = true },
                onOpenSoundDialog = { showSoundDialog = true }
            )

            GolfGameType.LUCKY_WOOD_TILE -> LuckyWoodTileGameScreen(
                settings = settings,
                soundEngine = soundEngine,
                showIntroCutscene = introCutsceneGame == target,
                onIntroCutsceneFinished = { if (introCutsceneGame == target) introCutsceneGame = null },
                onToggleBgmMute = ::handleToggleBgmMute,
                onBack = {
                    soundEngine.setClimaxDucking(false)
                    introCutsceneGame = null
                    activeGame = null
                },
                onOpenPlayerDialog = { showMidPlayerDialog = true },
                onOpenSoundDialog = { showSoundDialog = true }
            )

            GolfGameType.DOUBLE_DICE -> DoubleDiceGameScreen(
                settings = settings,
                soundEngine = soundEngine,
                showIntroCutscene = introCutsceneGame == target,
                onIntroCutsceneFinished = { if (introCutsceneGame == target) introCutsceneGame = null },
                onToggleBgmMute = ::handleToggleBgmMute,
                onBack = {
                    soundEngine.setClimaxDucking(false)
                    introCutsceneGame = null
                    activeGame = null
                },
                onOpenPlayerDialog = { showMidPlayerDialog = true },
                onOpenSoundDialog = { showSoundDialog = true }
            )

            GolfGameType.YUT_THROW -> YutNoriGameScreen(
                settings = settings,
                soundEngine = soundEngine,
                showIntroCutscene = introCutsceneGame == target,
                onIntroCutsceneFinished = { if (introCutsceneGame == target) introCutsceneGame = null },
                onToggleBgmMute = ::handleToggleBgmMute,
                onBack = {
                    soundEngine.setClimaxDucking(false)
                    introCutsceneGame = null
                    activeGame = null
                },
                onOpenPlayerDialog = { showMidPlayerDialog = true },
                onOpenSoundDialog = { showSoundDialog = true }
            )

            GolfGameType.TAROT_FORTUNE -> TarotFortuneGameScreen(
                settings = settings,
                soundEngine = soundEngine,
                showIntroCutscene = introCutsceneGame == target,
                onIntroCutsceneFinished = { if (introCutsceneGame == target) introCutsceneGame = null },
                onToggleBgmMute = ::handleToggleBgmMute,
                onBack = {
                    soundEngine.setClimaxDucking(false)
                    introCutsceneGame = null
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
            onToggleBgmMute = ::handleToggleBgmMute,
            onConfirm = { updated ->
                updateSettings(updated)
                pendingEntryGame = null
                introCutsceneGame = if (updated.videoEffectsEnabled) targetGame else null
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
            onToggleBgmMute = ::handleToggleBgmMute,
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

    // 앱 종료 및 최소화 전면 광고 직후 표시되는 2.5초 카운트다운 안내 카드
    val activeExitTarget = exitCountdownTarget
    if (activeExitTarget != null) {
        ExitCountdownOverlay(
            target = activeExitTarget,
            onCancel = { exitCountdownTarget = null },
            onSwitchAndExecute = { altTarget ->
                executeExitOrMinimize(altTarget)
            },
            onExecuteNow = { confirmedTarget ->
                executeExitOrMinimize(confirmedTarget)
            }
        )
    }
    }
    }
}
