package com.jjalddalmark.minigamerullet.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jjalddalmark.minigamerullet.R
import com.jjalddalmark.minigamerullet.ads.HomeBannerAdSlot
import com.jjalddalmark.minigamerullet.model.AppSettings
import com.jjalddalmark.minigamerullet.model.GameCategory
import com.jjalddalmark.minigamerullet.model.GolfGameType
import com.jjalddalmark.minigamerullet.ui.components.BgmMuteStatusToggleButton
import com.jjalddalmark.minigamerullet.ui.components.FloatingDandelionOverlay
import com.jjalddalmark.minigamerullet.ui.components.FloatingFairwayLeavesOverlay
import com.jjalddalmark.minigamerullet.ui.components.GameVectorThumbnail
import com.jjalddalmark.minigamerullet.ui.components.HomeSelectionBurstOverlay
import com.jjalddalmark.minigamerullet.ui.theme.InkMuted
import com.jjalddalmark.minigamerullet.ui.theme.InkPrimary
import com.jjalddalmark.minigamerullet.ui.theme.InkSecondary
import com.jjalddalmark.minigamerullet.ui.theme.MeadowGreen
import com.jjalddalmark.minigamerullet.ui.theme.OakWood
import com.jjalddalmark.minigamerullet.ui.theme.PaperCream
import com.jjalddalmark.minigamerullet.ui.theme.PineDeep
import com.jjalddalmark.minigamerullet.ui.theme.PureCardWhite
import com.jjalddalmark.minigamerullet.ui.theme.SubtleDivider
import com.jjalddalmark.minigamerullet.ui.theme.WarmIvory

/**
 * 지브리 수채화 감성의 메인 로비 화면
 */
@Composable
fun HomeScreen(
    settings: AppSettings,
    onSelectGame: (GolfGameType) -> Unit,
    onToggleBgmMute: () -> Unit,
    onOpenPlayerDialog: () -> Unit,
    onOpenSoundDialog: () -> Unit,
    onMinimizeApp: () -> Unit = {}
) {
    // 0: 전체, 1: 1홀 티샷 순서, 2: 커피·꼴등 복불복, 3: 주사위·윷·타로, 4: 만능 결정 룰렛+
    var filterTab by remember { mutableIntStateOf(0) }
    var selectionBurstKey by remember { mutableIntStateOf(0) }

    val filteredGames = remember(filterTab) {
        when (filterTab) {
            1 -> GolfGameType.entries.filter { it.category == GameCategory.TEE_ORDER }
            2 -> GolfGameType.entries.filter { it.category == GameCategory.COFFEE_PENALTY }
            3 -> GolfGameType.entries.filter { it.category == GameCategory.BOARD_FORTUNE }
            4 -> GolfGameType.entries.filter { it.category == GameCategory.DECISION_PICKER }
            else -> GolfGameType.entries
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PaperCream)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
        ) {
        LazyColumn(
            contentPadding = PaddingValues(bottom = 24.dp),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            // 1. 상단 지브리 페어웨이 풍경 배너 영역
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(228.dp)
                        .clipToBounds()
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.bg_fairway_meadow),
                        contentDescription = "미니게임 초원 풍경",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        PaperCream.copy(alpha = 0.12f),
                                        PaperCream.copy(alpha = 0.45f),
                                        PaperCream
                                    )
                                )
                            )
                    )
                    if (settings.videoEffectsEnabled) {
                        FloatingFairwayLeavesOverlay(
                            active = true,
                            isSlowMotion = false,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    FloatingDandelionOverlay(
                        isSlowMotion = false,
                        modifier = Modifier.fillMaxSize()
                    )

                    // 상단 배경음 음소거 상태 아이콘 토글 + 사운드·진행 속도 설정 바 + 최소화 버튼
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        BgmMuteStatusToggleButton(
                            isBgmMuted = settings.isBgmMuted,
                            onToggleBgmMute = onToggleBgmMute,
                            compact = false
                        )

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(18.dp))
                                .background(PureCardWhite.copy(alpha = 0.92f))
                                .border(1.dp, SubtleDivider, RoundedCornerShape(18.dp))
                                .clickable { onOpenSoundDialog() }
                                .padding(horizontal = 11.dp, vertical = 6.dp)
                        ) {
                            val soundTag = if (settings.soundEnabled) "사운드 설정" else "전체 음소거"
                            Text(
                                text = "$soundTag · ${settings.normalizedDurationSeconds}초",
                                style = MaterialTheme.typography.labelLarge,
                                color = if (settings.soundEnabled) PineDeep else InkMuted
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(18.dp))
                                .background(PureCardWhite.copy(alpha = 0.92f))
                                .border(1.dp, SubtleDivider, RoundedCornerShape(18.dp))
                                .clickable { onMinimizeApp() }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "최소화",
                                style = MaterialTheme.typography.labelLarge,
                                color = InkSecondary
                            )
                        }
                    }

                // 하단 타이틀 그룹
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = "MINIGAME · ROULETTE & GOLF",
                        style = MaterialTheme.typography.labelMedium,
                        color = PineDeep,
                        letterSpacing = 1.2.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "미니게임 - 만능 룰렛 & 골프 복불복",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontSize = 21.sp,
                            lineHeight = 28.sp,
                            letterSpacing = (-0.4).sp
                        ),
                        color = InkPrimary
                    )
                    Text(
                        text = "일상 만능 결정 룰렛+부터 티샷 순서·커피 내기·주사위·윷·타로까지 11종 수록",
                        style = MaterialTheme.typography.bodyMedium,
                        color = InkSecondary
                    )
                }
            }
        }

        // 2. 출전 선수 명단 ('선수입장' 원터치 프리셋 및 2클릭 이름 조합 진입)
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "출전 선수 명단 (${settings.players.size}명)",
                        style = MaterialTheme.typography.titleMedium,
                        color = InkPrimary
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(WarmIvory)
                            .border(1.dp, SubtleDivider, RoundedCornerShape(8.dp))
                            .clickable { onOpenPlayerDialog() }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = "선수입장 · 이름 조합",
                            style = MaterialTheme.typography.labelLarge,
                            color = MeadowGreen
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    settings.players.forEachIndexed { idx, player ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(24.dp))
                                .background(PureCardWhite)
                                .border(1.dp, SubtleDivider, RoundedCornerShape(24.dp))
                                .clickable { onOpenPlayerDialog() }
                                .padding(start = 6.dp, end = 12.dp, top = 5.dp, bottom = 5.dp)
                        ) {
                            Image(
                                painter = painterResource(id = player.avatarRes),
                                contentDescription = player.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .border(1.5.dp, player.badgeColor, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${idx + 1}. ${player.name}",
                                style = MaterialTheme.typography.labelLarge,
                                color = InkPrimary
                            )
                        }
                    }
                }
            }
        }

        // 3. 카테고리 필터 탭 바
        item {
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
            ) {
                val tabs = listOf(
                    "전체 11종",
                    "만능 결정 룰렛+",
                    "주사위 · 윷 · 타로",
                    "1홀 티샷 순서",
                    "커피 · 꼴등 복불복"
                )
                val tabToFilterIndex = listOf(0, 4, 3, 1, 2)
                tabs.forEachIndexed { uiIdx, label ->
                    val targetFilter = tabToFilterIndex[uiIdx]
                    val selected = filterTab == targetFilter
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selected) MeadowGreen else WarmIvory)
                            .border(
                                width = 1.dp,
                                color = if (selected) MeadowGreen else SubtleDivider,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable { filterTab = targetFilter }
                            .padding(vertical = 9.dp, horizontal = 13.dp)
                    ) {
                        Text(
                            text = label,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (selected) PureCardWhite else InkSecondary,
                            maxLines = 1
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
        }

        // 4. 게임 목록
        items(filteredGames, key = { it.name }) { game ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(PureCardWhite)
                        .border(1.dp, SubtleDivider, RoundedCornerShape(16.dp))
                        .clickable {
                            selectionBurstKey++
                            onSelectGame(game)
                        }
                        .padding(14.dp)
                ) {
                    GameVectorThumbnail(gameType = game)

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = game.category.label,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = game.accentColor
                            )
                            Text(
                                text = "·",
                                fontSize = 11.sp,
                                color = InkMuted
                            )
                            Text(
                                text = when {
                                    game == GolfGameType.DECISION_ROULETTE_PLUS ->
                                        "즉시 실행 · 저장 · 사진·영상 공유"
                                    game == GolfGameType.WINDMILL_ROULETTE ->
                                        "풍차 룰렛+ · 저장 · 사진·영상 공유"
                                    settings.videoEffectsEnabled ->
                                        "동영상 연출 · 사진·영상 공유"
                                    else ->
                                        "선수입장 · 사진·영상 공유"
                                },
                                fontSize = 11.sp,
                                color = OakWood
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = game.title,
                            style = MaterialTheme.typography.titleLarge,
                            color = InkPrimary
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = game.subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = InkSecondary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Canvas(modifier = Modifier.size(16.dp).clipToBounds()) {
                        val p = Path().apply {
                            moveTo(size.width * 0.32f, size.height * 0.18f)
                            lineTo(size.width * 0.72f, size.height * 0.50f)
                            lineTo(size.width * 0.32f, size.height * 0.82f)
                        }
                        drawPath(
                            path = p,
                            color = InkMuted,
                            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }
        }

        // 5. 하단 랜덤 게임 추천 실행 바
        item {
            Spacer(modifier = Modifier.height(6.dp))
            HorizontalDivider(
                color = SubtleDivider,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            Spacer(modifier = Modifier.height(14.dp))
            Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                Button(
                    onClick = {
                        selectionBurstKey++
                        val pool = filteredGames.ifEmpty { GolfGameType.entries }
                        onSelectGame(pool.random())
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = OakWood),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Text(
                        text = "고민 없이 랜덤 미니게임 선수입장",
                        style = MaterialTheme.typography.titleMedium,
                        color = PureCardWhite
                    )
                }
            }
        }
        }

        // 홈 화면 최하단 배너 광고 슬롯 (오프라인·미로드 시 빈 여백 없이 자동 접힘)
        HomeBannerAdSlot(visible = true)
        }

        if (settings.videoEffectsEnabled) {
            HomeSelectionBurstOverlay(
                burstTrigger = selectionBurstKey,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
