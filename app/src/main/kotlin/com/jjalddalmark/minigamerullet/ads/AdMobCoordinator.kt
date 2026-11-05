package com.jjalddalmark.minigamerullet.ads

import android.app.Activity
import android.content.Context
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.jjalddalmark.minigamerullet.R
import com.jjalddalmark.minigamerullet.ui.theme.InkPrimary
import com.jjalddalmark.minigamerullet.ui.theme.InkSecondary
import com.jjalddalmark.minigamerullet.ui.theme.MeadowGreen
import com.jjalddalmark.minigamerullet.ui.theme.MeadowGreenSoft
import com.jjalddalmark.minigamerullet.ui.theme.PineDeep
import com.jjalddalmark.minigamerullet.ui.theme.PureCardWhite
import com.jjalddalmark.minigamerullet.ui.theme.SubtleDivider
import com.jjalddalmark.minigamerullet.ui.theme.WarmIvory
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.ceil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 앱 종료 또는 홈 화면 최소화 동작 구분.
 */
enum class ExitActionTarget {
    EXIT_APP,
    MINIMIZE_APP
}

/**
 * Google AdMob 비동기 초기화, 홈 하단 배너 및 종료·최소화 전면 광고 코디네이터.
 * - 메인 UI 스레드 블로킹(ANR)을 막기 위해 MobileAds 초기화는 Dispatchers.IO에서 수행한다.
 * - 테스트 광고 ID 유입을 원천 차단하고 정식 게시자 ID 형식만 허용한다.
 * - 오프라인이거나 광고 미로드 상태일 때는 대기 지연 없이 즉시 종료·최소화 안내로 전환한다.
 */
object AdMobCoordinator {

    const val OFFICIAL_PUBLISHER_PREFIX = "ca-app-pub-5019798166308009"
    const val EXIT_COUNTDOWN_DURATION_MS = 2_500L
    const val INTERSTITIAL_COOLDOWN_MS = 60_000L

    private val appIdRegex = Regex("""^ca-app-pub-5019798166308009~\d{10}$""")
    private val unitIdRegex = Regex("""^ca-app-pub-5019798166308009/\d{10}$""")

    private val adScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val initStarted = AtomicBoolean(false)

    @Volatile
    private var sdkInitialized = false

    private var cachedInterstitial: InterstitialAd? = null
    private var isLoadingInterstitial = false
    private var lastInterstitialShownElapsedMs = -INTERSTITIAL_COOLDOWN_MS

    internal fun isValidOfficialAppId(candidate: String): Boolean {
        val trimmed = candidate.trim()
        return trimmed.startsWith("$OFFICIAL_PUBLISHER_PREFIX~") && appIdRegex.matches(trimmed)
    }

    internal fun isValidOfficialUnitId(candidate: String): Boolean {
        val trimmed = candidate.trim()
        return trimmed.startsWith("$OFFICIAL_PUBLISHER_PREFIX/") && unitIdRegex.matches(trimmed)
    }

    internal fun ensureOfficialAdUnitId(candidate: String): String {
        val trimmed = candidate.trim()
        return if (isValidOfficialUnitId(trimmed)) trimmed else ""
    }

    internal fun shouldShowInterstitialByCooldown(
        nowElapsedMs: Long,
        lastShownElapsedMs: Long,
        cooldownMs: Long = INTERSTITIAL_COOLDOWN_MS
    ): Boolean {
        return (nowElapsedMs - lastShownElapsedMs) >= cooldownMs
    }

    internal fun computeCountdownSeconds(remainingMs: Long): Int {
        if (remainingMs <= 0L) return 0
        return ceil(remainingMs / 1000.0).toInt().coerceIn(1, 3)
    }

    /**
     * 백그라운드 스레드(Dispatchers.IO)에서 Google Mobile Ads SDK를 초기화하고 종료·최소화용 전면 광고를 사전 로드한다.
     */
    fun initializeAsync(context: Context) {
        val appContext = context.applicationContext
        if (!initStarted.compareAndSet(false, true)) {
            if (sdkInitialized) {
                adScope.launch(Dispatchers.Main) {
                    preloadExitInterstitial(appContext)
                }
            }
            return
        }

        adScope.launch {
            runCatching {
                MobileAds.setRequestConfiguration(
                    RequestConfiguration.Builder()
                        .setTestDeviceIds(emptyList())
                        .build()
                )
                MobileAds.initialize(appContext) {
                    sdkInitialized = true
                    adScope.launch(Dispatchers.Main) {
                        preloadExitInterstitial(appContext)
                    }
                }
            }
        }
    }

    /**
     * 메인 스레드에서 앱 종료·최소화 시점용 전면 광고(InterstitialAd)를 사전 로드한다.
     */
    fun preloadExitInterstitial(context: Context) {
        val appContext = context.applicationContext
        if (cachedInterstitial != null || isLoadingInterstitial) return

        val rawUnitId = runCatching {
            appContext.getString(R.string.admob_interstitial_exit_unit_id)
        }.getOrDefault("")
        val unitId = ensureOfficialAdUnitId(rawUnitId)
        if (unitId.isEmpty()) return

        isLoadingInterstitial = true
        runCatching {
            InterstitialAd.load(
                appContext,
                unitId,
                AdRequest.Builder().build(),
                object : InterstitialAdLoadCallback() {
                    override fun onAdLoaded(ad: InterstitialAd) {
                        isLoadingInterstitial = false
                        cachedInterstitial = ad
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        isLoadingInterstitial = false
                        cachedInterstitial = null
                    }
                }
            )
        }.onFailure {
            isLoadingInterstitial = false
        }
    }

    /**
     * 준비된 전면 광고가 있고 쿨다운이 지났다면 즉시 표시한 뒤 닫힐 때 [onComplete]를 실행한다.
     * 오프라인이거나 미로드 상태일 때는 대기 없이 곧바로 [onComplete]로 이어진다.
     */
    fun showExitInterstitialThen(
        activity: Activity,
        onAdShown: () -> Unit = {},
        onComplete: () -> Unit
    ) {
        val invoked = AtomicBoolean(false)
        fun completeOnce() {
            if (invoked.compareAndSet(false, true)) {
                onComplete()
            }
        }

        if (activity.isFinishing || activity.isDestroyed) {
            completeOnce()
            return
        }

        val now = SystemClock.elapsedRealtime()
        val ad = cachedInterstitial
        if (ad == null || !shouldShowInterstitialByCooldown(
                nowElapsedMs = now,
                lastShownElapsedMs = lastInterstitialShownElapsedMs
            )
        ) {
            if (ad == null) {
                preloadExitInterstitial(activity.applicationContext)
            }
            completeOnce()
            return
        }

        cachedInterstitial = null
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                lastInterstitialShownElapsedMs = SystemClock.elapsedRealtime()
                onAdShown()
            }

            override fun onAdDismissedFullScreenContent() {
                ad.fullScreenContentCallback = null
                preloadExitInterstitial(activity.applicationContext)
                completeOnce()
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                ad.fullScreenContentCallback = null
                preloadExitInterstitial(activity.applicationContext)
                completeOnce()
            }
        }

        runCatching {
            ad.show(activity)
        }.onFailure {
            ad.fullScreenContentCallback = null
            preloadExitInterstitial(activity.applicationContext)
            completeOnce()
        }
    }
}

/**
 * 홈 로비 하단에 배치되는 슬림형 배너 광고 슬롯.
 * - 본 게임 화면에는 노출되지 않고 홈 화면 하단에만 분리 배치되어 룰렛·퍼팅 조작을 방해하지 않는다.
 * - 오프라인 등 광고 미로드 상태에서는 빈 여백 없이 접혀 있다가 로드 완료 시에만 부드럽게 표시된다.
 */
@Composable
fun HomeBannerAdSlot(
    modifier: Modifier = Modifier,
    visible: Boolean = true
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val rawUnitId = stringResource(id = R.string.admob_banner_home_unit_id)
    val resolvedUnitId = remember(rawUnitId) {
        AdMobCoordinator.ensureOfficialAdUnitId(rawUnitId)
    }
    if (resolvedUnitId.isEmpty()) return

    var isBannerLoaded by remember(resolvedUnitId) { mutableStateOf(false) }

    val adView = remember(context, resolvedUnitId) {
        AdView(context).apply {
            setAdSize(AdSize.BANNER)
            this.adUnitId = resolvedUnitId
            adListener = object : AdListener() {
                override fun onAdLoaded() {
                    isBannerLoaded = true
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    isBannerLoaded = false
                }
            }
            runCatching {
                loadAd(AdRequest.Builder().build())
            }
        }
    }

    DisposableEffect(lifecycleOwner, adView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> runCatching { adView.resume() }
                Lifecycle.Event.ON_PAUSE -> runCatching { adView.pause() }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            runCatching { adView.destroy() }
        }
    }

    val showActiveSlot = visible && isBannerLoaded
    val slotHeight = when {
        !visible -> 0.dp
        isBannerLoaded -> 52.dp
        else -> 1.dp
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(if (showActiveSlot) Modifier.background(WarmIvory) else Modifier)
            .height(slotHeight)
            .clipToBounds()
            .alpha(if (showActiveSlot) 1f else 0f),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (showActiveSlot) {
            HorizontalDivider(color = SubtleDivider, thickness = 1.dp)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (showActiveSlot) 51.dp else 1.dp),
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                factory = { ctx ->
                    FrameLayout(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                        (adView.parent as? ViewGroup)?.removeView(adView)
                        addView(
                            adView,
                            FrameLayout.LayoutParams(
                                FrameLayout.LayoutParams.WRAP_CONTENT,
                                FrameLayout.LayoutParams.WRAP_CONTENT,
                                Gravity.CENTER
                            )
                        )
                    }
                },
                update = { container ->
                    adView.visibility = if (visible) View.VISIBLE else View.GONE
                    container.visibility = if (visible) View.VISIBLE else View.GONE
                }
            )
        }
    }
}

/**
 * 전면 광고 종료 직후(또는 오프라인 시 즉시) 표시되는 자동 종료·최소화 카운트다운 안내 카드.
 * 실수로 뒤로가기나 최소화를 눌렀을 때 [계속 사용]으로 즉시 복귀할 수 있다.
 */
@Composable
fun ExitCountdownOverlay(
    target: ExitActionTarget,
    onCancel: () -> Unit,
    onSwitchAndExecute: (ExitActionTarget) -> Unit,
    onExecuteNow: (ExitActionTarget) -> Unit,
    modifier: Modifier = Modifier,
    durationMs: Long = AdMobCoordinator.EXIT_COUNTDOWN_DURATION_MS
) {
    val latestExecute by rememberUpdatedState(onExecuteNow)
    var remainingSeconds by remember(target) {
        mutableIntStateOf(AdMobCoordinator.computeCountdownSeconds(durationMs))
    }
    var progressFraction by remember(target) { mutableFloatStateOf(1f) }

    LaunchedEffect(target, durationMs) {
        val startElapsed = SystemClock.elapsedRealtime()
        while (true) {
            val elapsed = SystemClock.elapsedRealtime() - startElapsed
            val remain = (durationMs - elapsed).coerceAtLeast(0L)
            remainingSeconds = AdMobCoordinator.computeCountdownSeconds(remain)
            progressFraction = (remain.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
            if (remain <= 0L) {
                latestExecute(target)
                break
            }
            delay(40L)
        }
    }

    val isExit = target == ExitActionTarget.EXIT_APP
    val titleText = if (isExit) {
        "잠시 후 앱이 종료됩니다"
    } else {
        "잠시 후 앱이 최소화됩니다"
    }
    val bodyText = if (isExit) {
        "${remainingSeconds}초 후 미니게임 앱이 자동으로 종료됩니다."
    } else {
        "${remainingSeconds}초 후 홈 화면으로 최소화됩니다."
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0x661F2721))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            )
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 360.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(PureCardWhite)
                .border(1.dp, SubtleDivider, RoundedCornerShape(18.dp))
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = titleText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = InkPrimary
                )
                Box(
                    modifier = Modifier
                        .background(MeadowGreenSoft, RoundedCornerShape(8.dp))
                        .border(1.dp, MeadowGreen.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 9.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "${remainingSeconds}초",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = PineDeep
                    )
                }
            }

            Text(
                text = bodyText,
                style = MaterialTheme.typography.bodyMedium,
                color = InkSecondary
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(WarmIvory)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progressFraction)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(3.dp))
                        .background(MeadowGreen)
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, SubtleDivider)
                ) {
                    Text(
                        text = "계속 사용",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = InkPrimary
                    )
                }

                OutlinedButton(
                    onClick = {
                        onSwitchAndExecute(
                            if (isExit) ExitActionTarget.MINIMIZE_APP else ExitActionTarget.EXIT_APP
                        )
                    },
                    modifier = Modifier
                        .weight(0.85f)
                        .height(42.dp),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, SubtleDivider)
                ) {
                    Text(
                        text = if (isExit) "최소화" else "종료",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = InkSecondary
                    )
                }

                Button(
                    onClick = { onExecuteNow(target) },
                    modifier = Modifier
                        .weight(1.08f)
                        .height(42.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MeadowGreen,
                        contentColor = PureCardWhite
                    )
                ) {
                    Text(
                        text = if (isExit) "지금 종료" else "지금 최소화",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
