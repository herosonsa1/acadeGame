package com.example.fairwaypicnic.ui.home

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
import com.example.fairwaypicnic.R
import com.example.fairwaypicnic.model.AppSettings
import com.example.fairwaypicnic.model.GameCategory
import com.example.fairwaypicnic.model.GolfGameType
import com.example.fairwaypicnic.ui.components.FloatingDandelionOverlay
import com.example.fairwaypicnic.ui.components.GameVectorThumbnail
import com.example.fairwaypicnic.ui.theme.InkMuted
import com.example.fairwaypicnic.ui.theme.InkPrimary
import com.example.fairwaypicnic.ui.theme.InkSecondary
import com.example.fairwaypicnic.ui.theme.MeadowGreen
import com.example.fairwaypicnic.ui.theme.OakWood
import com.example.fairwaypicnic.ui.theme.PaperCream
import com.example.fairwaypicnic.ui.theme.PineDeep
import com.example.fairwaypicnic.ui.theme.PureCardWhite
import com.example.fairwaypicnic.ui.theme.SubtleDivider
import com.example.fairwaypicnic.ui.theme.WarmIvory

/**
 * 지브리 수채화 감성의 메인 로비 화면
 */
@Composable
fun HomeScreen(
    settings: AppSettings,
    onSelectGame: (GolfGameType) -> Unit,
    onOpenPlayerDialog: () -> Unit,
    onOpenSoundDialog: () -> Unit
) {
    // 0: 전체, 1: 1홀 티샷 순서, 2: 커피·꼴등 복불복
    var filterTab by remember { mutableIntStateOf(0) }

    val filteredGames = remember(filterTab) {
        when (filterTab) {
            1 -> GolfGameType.entries.filter { it.category == GameCategory.TEE_ORDER }
            2 -> GolfGameType.entries.filter { it.category == GameCategory.COFFEE_PENALTY }
            else -> GolfGameType.entries
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(bottom = 28.dp),
        modifier = Modifier
            .fillMaxSize()
            .background(PaperCream)
            .navigationBarsPadding()
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
                    contentDescription = "페어웨이 풍경",
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
                FloatingDandelionOverlay(
                    isSlowMotion = false,
                    modifier = Modifier.fillMaxSize()
                )

                // 우측 상단 사운드 · 진행 속도 설정 알약 버튼
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .background(PureCardWhite.copy(alpha = 0.92f))
                            .border(1.dp, SubtleDivider, RoundedCornerShape(18.dp))
                            .clickable { onOpenSoundDialog() }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        val soundTag = if (settings.soundEnabled) "사운드 설정" else "음소거"
                        Text(
                            text = "$soundTag · ${settings.normalizedDurationSeconds}초 진행",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (settings.soundEnabled) PineDeep else InkMuted
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
                        text = "FIELD ARCADE COLLECTION",
                        style = MaterialTheme.typography.labelMedium,
                        color = PineDeep,
                        letterSpacing = 1.2.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "페어웨이 소풍",
                        style = MaterialTheme.typography.headlineLarge,
                        color = InkPrimary
                    )
                    Text(
                        text = "1홀 티샷 순서부터 그늘집 커피 내기까지 즐기는 7가지 미니게임",
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
                    .padding(horizontal = 20.dp)
            ) {
                val tabs = listOf(
                    "전체 7종",
                    "1홀 티샷 순서",
                    "커피 · 꼴등 복불복"
                )
                tabs.forEachIndexed { idx, label ->
                    val selected = filterTab == idx
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selected) MeadowGreen else WarmIvory)
                            .border(
                                width = 1.dp,
                                color = if (selected) MeadowGreen else SubtleDivider,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable { filterTab = idx }
                            .padding(vertical = 9.dp, horizontal = 4.dp)
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
                        .clickable { onSelectGame(game) }
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
                                text = "선수입장 · ${settings.normalizedDurationSeconds}초 연출",
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
}
