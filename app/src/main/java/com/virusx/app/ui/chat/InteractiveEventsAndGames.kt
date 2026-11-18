package com.virusx.app.ui.chat

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.virusx.app.ui.theme.*
import com.virusx.app.util.VirusXEffectsAndNotifications
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlinx.coroutines.delay

// ============================================================================
// 1. LIVE ROULETTE WHEEL ENGINE (Canvas 2D + CubicBezier Physics Spin)
// ============================================================================

@Composable
fun InChatRouletteWheelCard(
    rawState: String,
    currentUserId: String,
    currentUserName: String,
    onUpdateState: (String) -> Unit
) {
    val parts = rawState.split("|")
    val title = parts.getOrNull(1)?.ifBlank { "قرعة الحظ المباشرة" } ?: "قرعة الحظ المباشرة"
    val playersRaw = parts.getOrNull(2).orEmpty()
    val status = parts.getOrNull(3) ?: "LOBBY"
    val targetAngle = parts.getOrNull(4)?.toFloatOrNull() ?: 0f
    val winnerName = parts.getOrNull(5).orEmpty()

    val participants = remember(playersRaw) {
        playersRaw.split(",").mapNotNull { entry ->
            val kv = entry.split(":")
            if (kv.size >= 2 && kv[0].isNotBlank()) kv[0] to kv[1] else null
        }
    }

    val isJoined = participants.any { it.first == currentUserId }
    val spinAnim = remember { Animatable(0f) }

    LaunchedEffect(status, targetAngle) {
        if (status == "SPIN" && targetAngle > 0f) {
            spinAnim.snapTo(0f)
            spinAnim.animateTo(
                targetValue = targetAngle,
                animationSpec = tween(
                    durationMillis = 4400,
                    easing = CubicBezierEasing(0.12f, 0.85f, 0.18f, 1.0f)
                )
            )
            VirusXEffectsAndNotifications.playSoundEffect("VICTORY")
        }
    }

    val sliceColors = remember {
        listOf(
            Color(0xFF6C5CE7),
            Color(0xFF00CEC9),
            Color(0xFFFD79A8),
            Color(0xFFFDCB6E),
            Color(0xFF0984E3),
            Color(0xFF00B894),
            Color(0xFFE17055),
            Color(0xFFA29BFE)
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF141824), Color(0xFF0C0F17))
                )
            )
            .border(1.2.dp, WarningAmber.copy(alpha = 0.65f), RoundedCornerShape(16.dp))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("🎡", fontSize = 18.sp)
            Text(
                text = title,
                color = WarningAmber,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Box(
            modifier = Modifier.size(196.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 8.dp)
            ) {
                val diameter = min(size.width, size.height)
                val radius = diameter / 2f
                val center = Offset(size.width / 2f, size.height / 2f)
                val count = participants.size.coerceAtLeast(1)
                val sweep = 360f / count
                val currentRot = spinAnim.value

                drawCircle(
                    color = Color(0xFFFFD54F),
                    radius = radius,
                    center = center,
                    style = Stroke(width = 6f)
                )

                for (i in 0 until count) {
                    val startAngle = (i * sweep + currentRot) % 360f
                    drawArc(
                        color = sliceColors[i % sliceColors.size],
                        startAngle = startAngle,
                        sweepAngle = sweep,
                        useCenter = true,
                        topLeft = Offset(center.x - radius + 4f, center.y - radius + 4f),
                        size = Size((radius - 4f) * 2f, (radius - 4f) * 2f)
                    )
                    val label = participants.getOrNull(i)?.second?.take(8) ?: "انضم الآن"
                    val midAngleRad = Math.toRadians((startAngle + sweep / 2f).toDouble())
                    val textR = radius * 0.60f
                    val tx = center.x + (textR * cos(midAngleRad)).toFloat()
                    val ty = center.y + (textR * sin(midAngleRad)).toFloat()

                    drawContext.canvas.nativeCanvas.apply {
                        val paint = Paint().apply {
                            color = android.graphics.Color.WHITE
                            textSize = 24f
                            textAlign = Paint.Align.CENTER
                            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                            isAntiAlias = true
                        }
                        drawText(label, tx, ty + 8f, paint)
                    }
                }

                drawCircle(color = Color(0xFF0A0D12), radius = 24f, center = center)
                drawCircle(color = Color(0xFFFFD54F), radius = 24f, center = center, style = Stroke(width = 4f))
            }

            Canvas(modifier = Modifier.size(22.dp)) {
                val path = Path().apply {
                    moveTo(size.width / 2f, size.height)
                    lineTo(0f, 0f)
                    lineTo(size.width, 0f)
                    close()
                }
                drawPath(path, color = Color(0xFFFF3B30))
            }
        }

        if (status == "SPIN" && spinAnim.value >= targetAngle * 0.96f && winnerName.isNotBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(OnlineGreen.copy(alpha = 0.18f))
                    .border(1.dp, OnlineGreen, RoundedCornerShape(12.dp))
                    .padding(vertical = 8.dp, horizontal = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "🎉 الفائز في القرعة: $winnerName 🏆",
                    color = OnlineGreen,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            Text(
                text = "المسجلون في العجلة (${participants.size}): ${participants.joinToString("، ") { it.second }.ifBlank { "لا يوجد بعد" }}",
                color = TextSecondary,
                fontSize = 11.sp,
                textAlign = TextAlign.Center
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (!isJoined && status == "LOBBY") {
                Button(
                    onClick = {
                        val cleanName = currentUserName.replace(",", "").replace(":", "").replace("|", "").ifBlank { "عضو" }
                        val updatedList = (participants + (currentUserId to cleanName))
                            .joinToString(",") { "${it.first}:${it.second}" }
                        onUpdateState("[EVENT_ROULETTE]|$title|$updatedList|LOBBY|0|")
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = VirusXPrimary),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("✋ سجل اسمي", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            if (participants.size >= 2) {
                Button(
                    onClick = {
                        val winIdx = (participants.indices).random()
                        val winner = participants[winIdx].second
                        val count = participants.size
                        val sweep = 360f / count
                        val targetSliceCenter = winIdx * sweep + sweep / 2f
                        val extraSpins = 360f * 5f
                        val finalDeg = extraSpins + ((270f - targetSliceCenter + 360f) % 360f)
                        val encodedPlayers = participants.joinToString(",") { "${it.first}:${it.second}" }
                        VirusXEffectsAndNotifications.playSoundEffect("DRUMROLL")
                        onUpdateState("[EVENT_ROULETTE]|$title|$encodedPlayers|SPIN|${finalDeg.toInt()}|$winner")
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = WarningAmber),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = if (status == "SPIN") "🔄 لف مرة أخرى" else "🎡 لف العجلة!",
                        color = Color.Black,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }
    }
}

// ============================================================================
// 2. THE IMPOSTER / SPYFALL GAME (Hold-to-Reveal Secret Role + Live Voting)
// ============================================================================

@Composable
fun InChatImposterGameCard(
    rawState: String,
    currentUserId: String,
    currentUserName: String,
    onUpdateState: (String) -> Unit
) {
    val parts = rawState.split("|")
    val category = parts.getOrNull(1) ?: "عام"
    val secretWord = parts.getOrNull(2) ?: "كشري"
    val imposterUid = parts.getOrNull(3).orEmpty()
    val playersRaw = parts.getOrNull(4).orEmpty()
    val status = parts.getOrNull(5) ?: "LOBBY"
    val votesRaw = parts.getOrNull(6).orEmpty()

    val players = remember(playersRaw) {
        playersRaw.split(",").mapNotNull { item ->
            val kv = item.split(":")
            if (kv.size >= 2 && kv[0].isNotBlank()) kv[0] to kv[1] else null
        }
    }

    val votesMap = remember(votesRaw) {
        votesRaw.split(",").mapNotNull { item ->
            val kv = item.split(">")
            if (kv.size >= 2 && kv[0].isNotBlank()) kv[0] to kv[1] else null
        }.toMap()
    }

    val isJoined = players.any { it.first == currentUserId }
    var isHoldingReveal by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF1F1124), Color(0xFF100A16))
                )
            )
            .border(1.2.dp, Color(0xFFFF4757).copy(alpha = 0.7f), RoundedCornerShape(16.dp))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("🕵️‍♂️", fontSize = 18.sp)
            Text(
                text = "لعبة الأمبوستر (الجاسوس) • $category",
                color = Color(0xFFFF6B81),
                fontWeight = FontWeight.ExtraBold,
                fontSize = 13.sp
            )
        }

        when (status) {
            "LOBBY" -> {
                Text(
                    text = "اللاعبون المنضمون (${players.size}): ${players.joinToString("، ") { it.second }.ifBlank { "بانتظار اللاعبين..." }}",
                    color = TextPrimary,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "💡 الكل سيعرف الكلمة السرية ما عدا (الأمبوستر) عليه التظاهر بمعرفتها!",
                    color = TextSecondary,
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    if (!isJoined) {
                        Button(
                            onClick = {
                                val clean = currentUserName.replace(",", "").replace(":", "").replace("|", "").ifBlank { "لاعب" }
                                val nextPlayers = (players + (currentUserId to clean))
                                    .joinToString(",") { "${it.first}:${it.second}" }
                                onUpdateState("[GAME_IMPOSTER]|$category|$secretWord|$imposterUid|$nextPlayers|LOBBY|")
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = VirusXPrimary),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("✋ انضمام للجولة", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (players.size >= 2) {
                        Button(
                            onClick = {
                                val chosenImposter = players.random().first
                                val encoded = players.joinToString(",") { "${it.first}:${it.second}" }
                                VirusXEffectsAndNotifications.playSoundEffect("SHOCK")
                                onUpdateState("[GAME_IMPOSTER]|$category|$secretWord|$chosenImposter|$encoded|PLAYING|")
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = OnlineGreen),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("🚀 توزيع الأدوار وبدء اللعب", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
            }

            "PLAYING" -> {
                if (isJoined) {
                    val amIImposter = currentUserId == imposterUid
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isHoldingReveal) {
                                    if (amIImposter) Color(0xFF4A0E17) else Color(0xFF0E3A2A)
                                } else VirusXSurfaceElevated
                            )
                            .border(
                                1.dp,
                                if (isHoldingReveal) WarningAmber else VirusXSecondaryWhisper,
                                RoundedCornerShape(12.dp)
                            )
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onPress = {
                                        isHoldingReveal = true
                                        tryAwaitRelease()
                                        isHoldingReveal = false
                                    }
                                )
                            }
                            .padding(vertical = 12.dp, horizontal = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isHoldingReveal) {
                            Text(
                                text = if (amIImposter) "🚨 أنت الأمبوستر (الجاسوس)!\nالتصنيف هو ($category) — اخدعهم وتظاهر أنك تعرف الكلمة!"
                                else "✅ أنت مواطن بريء!\nالكلمة السرية هي: 【 $secretWord 】",
                                color = if (amIImposter) Color(0xFFFF6B81) else OnlineGreen,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center
                            )
                        } else {
                            Text(
                                text = "👁️ اضغط مطولاً بإصبعك هنا لكشف دورك السري (يرتفع الإخفاء فور ترك إصبعك)",
                                color = WarningAmber,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    Text("الجولة جارية حالياً بين (${players.size}) لاعبين — راقب النقاش!", color = TextSecondary, fontSize = 11.sp)
                }

                Text(
                    text = "🗳️ صوّت على من تشك أنه الأمبوستر (${votesMap.size}/${players.size}):",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )

                Column(verticalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.fillMaxWidth()) {
                    players.forEach { (pid, pname) ->
                        val votesForThis = votesMap.values.count { it == pid }
                        val myVote = votesMap[currentUserId] == pid
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (myVote) Color(0xFF3B1B2E) else VirusXSurfaceElevated)
                                .border(1.dp, if (myVote) Color(0xFFFF6B81) else VirusXGlassBorder, RoundedCornerShape(10.dp))
                                .clickable(enabled = isJoined) {
                                    val updatedVotes = votesMap.toMutableMap().apply { put(currentUserId, pid) }
                                    val encodedVotes = updatedVotes.entries.joinToString(",") { "${it.key}>${it.value}" }
                                    val encodedPlayers = players.joinToString(",") { "${it.first}:${it.second}" }
                                    val nextStatus = if (updatedVotes.size >= players.size) "REVEALED" else "PLAYING"
                                    onUpdateState("[GAME_IMPOSTER]|$category|$secretWord|$imposterUid|$encodedPlayers|$nextStatus|$encodedVotes")
                                }
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(pname, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, modifier = Modifier.weight(1f))
                            Text(
                                text = if (votesForThis > 0) "🔥 $votesForThis أصوات" else "صوّت ضده",
                                color = if (votesForThis > 0) WarningAmber else TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                if (votesMap.isNotEmpty()) {
                    TextButton(
                        onClick = {
                            val encodedPlayers = players.joinToString(",") { "${it.first}:${it.second}" }
                            val encodedVotes = votesMap.entries.joinToString(",") { "${it.key}>${it.value}" }
                            VirusXEffectsAndNotifications.playSoundEffect("DRUMROLL")
                            onUpdateState("[GAME_IMPOSTER]|$category|$secretWord|$imposterUid|$encodedPlayers|REVEALED|$encodedVotes")
                        }
                    ) {
                        Text("⚖️ إنهاء النقاش وكشف الأمبوستر الآن", color = WarningAmber, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            else -> {
                val imposterName = players.firstOrNull { it.first == imposterUid }?.second ?: "غير معروف"
                val mostVotedId = votesMap.values.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
                val groupCaughtImposter = mostVotedId == imposterUid

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (groupCaughtImposter) OnlineGreen.copy(alpha = 0.18f) else DangerRed.copy(alpha = 0.22f))
                        .border(1.dp, if (groupCaughtImposter) OnlineGreen else DangerRed, RoundedCornerShape(12.dp))
                        .padding(10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = if (groupCaughtImposter) "🎉 انتصر الأبرياء! تم القبض على الأمبوستر!"
                            else "😈 انتصر الأمبوستر وخدع الجميع!",
                            color = if (groupCaughtImposter) OnlineGreen else Color(0xFFFF6B81),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                        Text("🕵️‍♂️ الأمبوستر الحقيقي كان: $imposterName", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("🔑 الكلمة السرية كانت: $secretWord", color = WarningAmber, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ============================================================================
// 3. TIME BOMB WORD CHALLENGE (💣 القنبلة الموقوتة)
// ============================================================================

@Composable
fun InChatWordBombCard(
    rawState: String,
    currentUserName: String,
    onUpdateState: (String) -> Unit
) {
    val parts = rawState.split("|")
    val targetLetters = parts.getOrNull(1) ?: "م ر"
    val lastPlayer = parts.getOrNull(2) ?: "البداية"
    val deadlineSec = parts.getOrNull(3)?.toLongOrNull() ?: (System.currentTimeMillis() / 1000L + 15L)
    val streak = parts.getOrNull(4)?.toIntOrNull() ?: 0
    val status = parts.getOrNull(5) ?: "ACTIVE"
    val usedWordsRaw = parts.getOrNull(6).orEmpty()

    var wordInput by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }
    var secondsLeft by remember(deadlineSec, status) {
        mutableIntStateOf(((deadlineSec - System.currentTimeMillis() / 1000L).toInt()).coerceAtLeast(0))
    }

    val pulseTransition = rememberInfiniteTransition(label = "bombPulse")
    val bombScale by pulseTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (status == "ACTIVE" && secondsLeft <= 5) 1.22f else 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (secondsLeft <= 5) 260 else 550, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    LaunchedEffect(deadlineSec, status) {
        if (status == "ACTIVE") {
            while (true) {
                val rem = (deadlineSec - System.currentTimeMillis() / 1000L).toInt()
                if (rem <= 0) {
                    secondsLeft = 0
                    VirusXEffectsAndNotifications.playSoundEffect("GAMEOVER")
                    onUpdateState("[GAME_BOMB]|$targetLetters|$lastPlayer|$deadlineSec|$streak|BOOM|$usedWordsRaw")
                    break
                }
                secondsLeft = rem
                delay(400L)
            }
        }
    }

    val letterPairs = remember {
        listOf("م ر", "س ل", "ك ت", "ح ب", "ع ل", "ش ر", "ن ص", "ف ه", "ق د", "ج م", "ب ح", "ط ر")
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF28120D), Color(0xFF120806))
                )
            )
            .border(1.2.dp, if (status == "BOOM") DangerRed else WarningAmber, RoundedCornerShape(16.dp))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = if (status == "BOOM") "💥" else "💣",
                fontSize = 26.sp,
                modifier = Modifier.scale(if (status == "ACTIVE") bombScale else 1f)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text("تحدي القنبلة الموقوتة بالكلمات", color = WarningAmber, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                Text("سلسلة النجاة المتتالية: $streak كلمات 🔥", color = OnlineGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            if (status == "ACTIVE") {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (secondsLeft <= 5) DangerRed else Color(0xFF3A2312))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("⏱️ ${secondsLeft}ث", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                }
            }
        }

        if (status == "BOOM") {
            Text(
                text = "💥 انفجرت القنبلة! توقفت السلسلة عند ($streak) كلمات (آخر ناجٍ: $lastPlayer)",
                color = DangerRed,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
            Button(
                onClick = {
                    val nextPair = letterPairs.random()
                    val nextDeadline = System.currentTimeMillis() / 1000L + 18L
                    onUpdateState("[GAME_BOMB]|$nextPair|$currentUserName|$nextDeadline|0|ACTIVE|")
                },
                colors = ButtonDefaults.buttonColors(containerColor = WarningAmber)
            ) {
                Text("🔄 إشعال قنبلة جديدة", color = Color.Black, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.35f))
                    .padding(vertical = 8.dp, horizontal = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "اكتب بسرعة كلمة تحتوي على الحرفين: 【 $targetLetters 】",
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }

            localError?.let { err ->
                Text(err, color = DangerRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedTextField(
                    value = wordInput,
                    onValueChange = {
                        wordInput = it
                        localError = null
                    },
                    placeholder = { Text("الكلمة هنا...", fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = {
                        val candidate = wordInput.trim()
                        val reqChars = targetLetters.split(" ").filter { it.isNotBlank() }
                        val usedSet = usedWordsRaw.split(",").filter { it.isNotBlank() }.toSet()
                        when {
                            candidate.length < 2 -> localError = "⚠️ اكتب كلمة حقيقية من حرفين أو أكثر"
                            !reqChars.all { ch -> candidate.contains(ch) } ->
                                localError = "⚠️ الكلمة يجب أن تحتوي على (${reqChars.joinToString(" و ")})"
                            usedSet.contains(candidate) ->
                                localError = "⚠️ هذه الكلمة استُخدمت بالفعل في هذه الجولة!"
                            else -> {
                                wordInput = ""
                                localError = null
                                val nextPair = letterPairs.random()
                                val nextDeadline = System.currentTimeMillis() / 1000L + 16L
                                val nextUsed = (usedSet + candidate).toList().takeLast(20).joinToString(",")
                                VirusXEffectsAndNotifications.playSoundEffect("VICTORY")
                                onUpdateState("[GAME_BOMB]|$nextPair|$currentUserName|$nextDeadline|${streak + 1}|ACTIVE|$nextUsed")
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = OnlineGreen)
                ) {
                    Text("تمرير 💣", color = Color.Black, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                }
            }
        }
    }
}

// ============================================================================
// 4. GROUP HOT SEAT / COURTROOM (⚖️ كرسي الاعتراف / محكمة الجروب)
// ============================================================================

@Composable
fun InChatHotSeatCourtCard(
    rawState: String,
    currentUserName: String,
    onUpdateState: (String) -> Unit
) {
    val parts = rawState.split("|")
    val accusedName = parts.getOrNull(1) ?: "عضو"
    val questionText = parts.getOrNull(2).orEmpty()
    val askedBy = parts.getOrNull(3).orEmpty()
    val answerText = parts.getOrNull(4).orEmpty()
    val status = parts.getOrNull(5) ?: "WAITING_Q"

    var inputDraft by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF1A1528), Color(0xFF0E0B16))
                )
            )
            .border(1.2.dp, Color(0xFFA29BFE), RoundedCornerShape(16.dp))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("⚖️", fontSize = 18.sp)
            Text(
                text = "محكمة الجروب وكرسي الاعتراف",
                color = Color(0xFFA29BFE),
                fontWeight = FontWeight.ExtraBold,
                fontSize = 13.sp
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(VirusXSurfaceElevated)
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "🔥 المتهم على كرسي الاعتراف: 【 $accusedName 】",
                color = WarningAmber,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 12.sp
            )
        }

        if (questionText.isNotBlank()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Black.copy(alpha = 0.3f))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("❓ سؤال المحكمة (من $askedBy):", color = VirusXSecondaryWhisper, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text(questionText, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)

                if (answerText.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (status == "ESCAPED") "🏃‍♂️ $answerText" else "💬 اعتراف $accusedName: $answerText",
                        color = if (status == "ESCAPED") DangerRed else OnlineGreen,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            OutlinedTextField(
                value = inputDraft,
                onValueChange = { inputDraft = it },
                placeholder = {
                    Text(
                        if (questionText.isBlank() || answerText.isNotBlank()) "وجّه سؤالاً جريئاً للمتهم..."
                        else "اكتب اعترافك أو سؤالك...",
                        fontSize = 11.sp
                    )
                },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )

            Button(
                onClick = {
                    val clean = inputDraft.trim().replace("|", " ")
                    if (clean.isNotBlank()) {
                        inputDraft = ""
                        if (questionText.isBlank() || answerText.isNotBlank()) {
                            VirusXEffectsAndNotifications.playSoundEffect("SHOCK")
                            onUpdateState("[EVENT_HOTSEAT]|$accusedName|$clean|$currentUserName||WAITING_A")
                        } else {
                            VirusXEffectsAndNotifications.playSoundEffect("VICTORY")
                            onUpdateState("[EVENT_HOTSEAT]|$accusedName|$questionText|$askedBy|$clean|ANSWERED")
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = VirusXPrimary)
            ) {
                Text(
                    text = if (questionText.isBlank() || answerText.isNotBlank()) "طرح سؤال" else "اعتراف",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (questionText.isNotBlank() && answerText.isBlank()) {
            TextButton(
                onClick = {
                    VirusXEffectsAndNotifications.playSoundEffect("GAMEOVER")
                    onUpdateState("[EVENT_HOTSEAT]|$accusedName|$questionText|$askedBy|استخدم كارت الهروب التكتيكي ورفض الإجابة! 🃏|ESCAPED")
                }
            ) {
                Text("🃏 استخدام كارت الهروب من السؤال", color = WarningAmber, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ============================================================================
// 5. PRO DOMINOES TABLE 2.0 (Real Engraved Pips + Vertical Doubles + Snake Board + 3-Level Local AI Bot)
// Format: [GAME_DOMINO]|boardTilesCsv(6-6,6-4)|p1Id:p1Name:handCsv|p2Id:p2Name:handCsv|boneyardCsv|turnUid|status
// ============================================================================

@Composable
fun InChatDominoTableCard(
    rawState: String,
    currentUserId: String,
    currentUserName: String,
    onUpdateState: (String) -> Unit
) {
    // Support 100% Local AI Mode (0 server traffic during gameplay) as well as Live Multiplayer sync
    var isLocalBotMode by remember { mutableStateOf(false) }
    var botDifficulty by remember { mutableStateOf("MEDIUM") } // EASY, MEDIUM, PRO
    var showBotLevelPicker by remember { mutableStateOf(false) }
    var isFullScreenTable by remember { mutableStateOf(false) }
    var localGameState by remember(rawState) {
        mutableStateOf(rawState)
    }

    val activeStateString = if (isLocalBotMode) localGameState else rawState

    fun dispatchStateUpdate(nextState: String, forceServerSync: Boolean = false) {
        localGameState = nextState
        if (!isLocalBotMode || forceServerSync) {
            onUpdateState(nextState)
        }
    }

    val parts = activeStateString.split("|")
    val boardRaw = parts.getOrNull(1).orEmpty()
    val p1Raw = parts.getOrNull(2).orEmpty()
    val p2Raw = parts.getOrNull(3).orEmpty()
    val boneyardRaw = parts.getOrNull(4).orEmpty()
    val turnUid = parts.getOrNull(5).orEmpty()
    val status = parts.getOrNull(6) ?: "WAITING_P2"

    fun parsePlayerSlot(slot: String): Triple<String, String, List<String>> {
        val sp = slot.split(":")
        val uid = sp.getOrNull(0).orEmpty()
        val name = sp.getOrNull(1).orEmpty()
        val tiles = sp.getOrNull(2).orEmpty().split(",").filter { it.contains("-") }
        return Triple(uid, name, tiles)
    }

    val (p1Id, p1Name, p1Hand) = remember(p1Raw) { parsePlayerSlot(p1Raw) }
    val (p2Id, p2Name, p2Hand) = remember(p2Raw) { parsePlayerSlot(p2Raw) }
    val boardTiles = remember(boardRaw) { boardRaw.split(",").filter { it.contains("-") } }
    val boneyard = remember(boneyardRaw) { boneyardRaw.split(",").filter { it.contains("-") } }

    val isPlayer1 = currentUserId == p1Id || (isLocalBotMode && p2Id.startsWith("BOT_"))
    val isPlayer2 = !isPlayer1 && currentUserId == p2Id
    val myHand = when {
        isPlayer1 -> p1Hand
        isPlayer2 -> p2Hand
        else -> emptyList()
    }
    val isMyTurn = (status == "ACTIVE") && ((isPlayer1 && turnUid == p1Id) || (isPlayer2 && turnUid == p2Id))
    val isBotTurn = (status == "ACTIVE") && p2Id.startsWith("BOT_") && turnUid == p2Id

    val leftEnd: Int? = remember(boardTiles) {
        boardTiles.firstOrNull()?.split("-")?.firstOrNull()?.toIntOrNull()
    }
    val rightEnd: Int? = remember(boardTiles) {
        boardTiles.lastOrNull()?.split("-")?.getOrNull(1)?.toIntOrNull()
    }

    fun tileSum(tile: String): Int {
        val ab = tile.split("-")
        return (ab.getOrNull(0)?.toIntOrNull() ?: 0) + (ab.getOrNull(1)?.toIntOrNull() ?: 0)
    }

    fun canPlayTileOnEnds(tile: String, lEnd: Int?, rEnd: Int?): Boolean {
        if (boardTiles.isEmpty() || lEnd == null || rEnd == null) return true
        val ab = tile.split("-")
        val a = ab.getOrNull(0)?.toIntOrNull() ?: return false
        val b = ab.getOrNull(1)?.toIntOrNull() ?: return false
        return a == lEnd || b == lEnd || a == rEnd || b == rEnd
    }

    fun attachTileToBoard(tile: String, currentBoard: List<String>, preferRight: Boolean = false): List<String>? {
        val ab = tile.split("-")
        val a = ab.getOrNull(0)?.toIntOrNull() ?: return null
        val b = ab.getOrNull(1)?.toIntOrNull() ?: return null
        if (currentBoard.isEmpty()) return listOf(tile)
        val lEnd = currentBoard.first().split("-")[0].toInt()
        val rEnd = currentBoard.last().split("-")[1].toInt()

        val canLeft = (a == lEnd || b == lEnd)
        val canRight = (a == rEnd || b == rEnd)

        return when {
            preferRight && canRight -> {
                if (a == rEnd) currentBoard + listOf("$a-$b") else currentBoard + listOf("$b-$a")
            }
            canLeft -> {
                if (b == lEnd) listOf("$a-$b") + currentBoard else listOf("$b-$a")
            }
            canRight -> {
                if (a == rEnd) currentBoard + listOf("$a-$b") else currentBoard + listOf("$b-$a")
            }
            else -> null
        }
    }

    fun evaluateLockedBoardWinner(hand1: List<String>, hand2: List<String>): String {
        val sum1 = hand1.sumOf { tileSum(it) }
        val sum2 = hand2.sumOf { tileSum(it) }
        VirusXEffectsAndNotifications.playSoundEffect("VICTORY")
        return when {
            sum1 < sum2 -> "WIN:$p1Name (كسب بقفل الطاولة $sum1 مقابل $sum2)"
            sum2 < sum1 -> "WIN:$p2Name (كسب بقفل الطاولة $sum2 مقابل $sum1)"
            else -> "WIN:تعادل بقفل الطاولة ($sum1 نقطة لكل طرف)"
        }
    }

    fun executeHumanPlay(tile: String) {
        if (!isMyTurn) return
        val nextBoard = attachTileToBoard(tile, boardTiles) ?: return
        val nextMyHand = myHand.toMutableList().also { it.remove(tile) }
        val nextP1Hand = if (isPlayer1) nextMyHand else p1Hand
        val nextP2Hand = if (isPlayer2) nextMyHand else p2Hand

        val isWin = nextMyHand.isEmpty()
        val nextStatus = if (isWin) {
            VirusXEffectsAndNotifications.playSoundEffect("VICTORY")
            "WIN:${if (isPlayer1) p1Name else p2Name} (دومينو! 🏆)"
        } else "ACTIVE"

        val nextTurn = if (isPlayer1) p2Id else p1Id
        val encoded = "[GAME_DOMINO]|${nextBoard.joinToString(",")}|$p1Id:$p1Name:${nextP1Hand.joinToString(",")}|$p2Id:$p2Name:${nextP2Hand.joinToString(",")}|${boneyard.joinToString(",")}|$nextTurn|$nextStatus"
        dispatchStateUpdate(encoded, forceServerSync = isWin)
    }

    fun executeHumanDrawOrPass() {
        if (!isMyTurn) return
        if (boneyard.isNotEmpty()) {
            val drawn = boneyard.first()
            val nextBoneyard = boneyard.drop(1)
            val nextMyHand = myHand + drawn
            val nextP1Hand = if (isPlayer1) nextMyHand else p1Hand
            val nextP2Hand = if (isPlayer2) nextMyHand else p2Hand
            val encoded = "[GAME_DOMINO]|${boardTiles.joinToString(",")}|$p1Id:$p1Name:${nextP1Hand.joinToString(",")}|$p2Id:$p2Name:${nextP2Hand.joinToString(",")}|${nextBoneyard.joinToString(",")}|$turnUid|ACTIVE"
            dispatchStateUpdate(encoded)
        } else {
            // Check if opponent is also blocked -> Lock Game!
            val oppHand = if (isPlayer1) p2Hand else p1Hand
            val oppHasMove = oppHand.any { canPlayTileOnEnds(it, leftEnd, rightEnd) }
            val nextStatus = if (!oppHasMove) evaluateLockedBoardWinner(p1Hand, p2Hand) else "ACTIVE"
            val nextTurn = if (isPlayer1) p2Id else p1Id
            val encoded = "[GAME_DOMINO]|${boardTiles.joinToString(",")}|$p1Id:$p1Name:${p1Hand.joinToString(",")}|$p2Id:$p2Name:${p2Hand.joinToString(",")}|${boneyard.joinToString(",")}|$nextTurn|$nextStatus"
            dispatchStateUpdate(encoded, forceServerSync = nextStatus.startsWith("WIN:"))
        }
    }

    // Smart Rule-Based Local AI Bot Engine (Easy / Medium / Pro) - 0 Server Cost!
    LaunchedEffect(activeStateString, isBotTurn, isLocalBotMode) {
        if (isBotTurn) {
            delay(850L) // Natural thinking pause
            var currentBotHand = p2Hand.toMutableList()
            var currentBoneyard = boneyard.toMutableList()

            // Draw from boneyard until bot finds a playable tile or boneyard empties
            while (currentBotHand.none { canPlayTileOnEnds(it, leftEnd, rightEnd) } && currentBoneyard.isNotEmpty()) {
                currentBotHand.add(currentBoneyard.removeAt(0))
                val midState = "[GAME_DOMINO]|${boardTiles.joinToString(",")}|$p1Id:$p1Name:${p1Hand.joinToString(",")}|$p2Id:$p2Name:${currentBotHand.joinToString(",")}|${currentBoneyard.joinToString(",")}|$p2Id|ACTIVE"
                dispatchStateUpdate(midState)
                delay(480L)
            }

            val playableTiles = currentBotHand.filter { canPlayTileOnEnds(it, leftEnd, rightEnd) }
            if (playableTiles.isEmpty()) {
                // Bot must pass (or lock board if human also has no move)
                val humanHasMove = p1Hand.any { canPlayTileOnEnds(it, leftEnd, rightEnd) }
                val nextStatus = if (!humanHasMove && currentBoneyard.isEmpty()) {
                    evaluateLockedBoardWinner(p1Hand, currentBotHand)
                } else "ACTIVE"
                val passState = "[GAME_DOMINO]|${boardTiles.joinToString(",")}|$p1Id:$p1Name:${p1Hand.joinToString(",")}|$p2Id:$p2Name:${currentBotHand.joinToString(",")}|$p1Id|$nextStatus"
                dispatchStateUpdate(passState, forceServerSync = nextStatus.startsWith("WIN:"))
            } else {
                // Choose tile based on Bot Difficulty Level
                val chosenTile = when (botDifficulty) {
                    "EASY" -> playableTiles.random()
                    "MEDIUM" -> {
                        // Medium: prioritizes doubles & heavy pip count
                        playableTiles.maxByOrNull { t ->
                            val ab = t.split("-")
                            val a = ab[0].toInt()
                            val b = ab[1].toInt()
                            (if (a == b) 30 else 0) + (a + b)
                        } ?: playableTiles.first()
                    }
                    else -> {
                        // PRO (حريف): Heuristic tactical scoring
                        // 1. High double priority (+45)
                        // 2. Heavy pip weight (+3 * sum)
                        // 3. Frequency control: prefers leaving an end number that the bot holds multiple of in its remaining hand (+18 per matching tile)
                        val numberFreq = IntArray(7)
                        currentBotHand.forEach { t ->
                            val ab = t.split("-")
                            val a = ab[0].toInt()
                            val b = ab[1].toInt()
                            numberFreq[a]++
                            if (b != a) numberFreq[b]++
                        }
                        playableTiles.maxByOrNull { t ->
                            val ab = t.split("-")
                            val a = ab[0].toInt()
                            val b = ab[1].toInt()
                            val isDouble = (a == b)
                            val doubleScore = if (isDouble) (35 + a * 3) else 0
                            val weightScore = (a + b) * 3
                            val resultingBoard = attachTileToBoard(t, boardTiles) ?: boardTiles
                            val newL = resultingBoard.first().split("-")[0].toInt()
                            val newR = resultingBoard.last().split("-")[1].toInt()
                            val controlScore = (numberFreq[newL] + numberFreq[newR]) * 16
                            doubleScore + weightScore + controlScore
                        } ?: playableTiles.first()
                    }
                }

                val nextBoard = attachTileToBoard(chosenTile, boardTiles) ?: boardTiles
                currentBotHand.remove(chosenTile)
                val botWon = currentBotHand.isEmpty()
                val nextStatus = if (botWon) {
                    VirusXEffectsAndNotifications.playSoundEffect("GAMEOVER")
                    "WIN:$p2Name (دومينو! 🤖)"
                } else "ACTIVE"

                val nextState = "[GAME_DOMINO]|${nextBoard.joinToString(",")}|$p1Id:$p1Name:${p1Hand.joinToString(",")}|$p2Id:$p2Name:${currentBotHand.joinToString(",")}|${currentBoneyard.joinToString(",")}|$p1Id|$nextStatus"
                dispatchStateUpdate(nextState, forceServerSync = botWon)
            }
        }
    }

    @Composable
    fun RenderDominoTableContent(isExpandedModal: Boolean) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF0A291E), Color(0xFF051610))
                    )
                )
                .border(1.5.dp, Color(0xFFD4AF37), RoundedCornerShape(16.dp))
                .padding(if (isExpandedModal) 16.dp else 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Clean Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("🁣 طاولة الدومينو الملكية", color = Color(0xFFFFD54F), fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                    if (isLocalBotMode) {
                        val badgeTxt = when (botDifficulty) {
                            "EASY" -> "🟢 سهل"
                            "MEDIUM" -> "🟡 متوسط"
                            else -> "🔴 حريف"
                        }
                        Text(
                            text = badgeTxt,
                            color = Color.Black,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 9.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(WarningAmber)
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("السوق: ${boneyard.size}", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text(
                        text = if (isExpandedModal) "✕ تصغير" else "⛶ تكبير",
                        color = OnlineGreen,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.35f))
                            .clickable { isFullScreenTable = !isExpandedModal }
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }
            }

            // STAGE 1: PROGRESSIVE DISCLOSURE LOBBY (Zero Button Clutter!)
            if (status == "WAITING_P2") {
                Text(
                    text = "الطاولة جاهزة ($p1Name) • اختر اللعب مع صديق أو تحدي البوت الذكي:",
                    color = TextPrimary,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center
                )

                if (!showBotLevelPicker) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (currentUserId != p1Id) {
                            Button(
                                onClick = {
                                    val cleanName = currentUserName.replace(":", "").replace("|", "").replace(",", "").ifBlank { "اللاعب 2" }
                                    dispatchStateUpdate(
                                        "[GAME_DOMINO]||$p1Id:$p1Name:${p1Hand.joinToString(",")}|$currentUserId:$cleanName:${p2Hand.joinToString(",")}|${boneyard.joinToString(",")}|$p1Id|ACTIVE",
                                        forceServerSync = true
                                    )
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = OnlineGreen),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("🪑 جلوس ضد $p1Name", color = Color.Black, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp)
                            }
                        } else {
                            OutlinedButton(
                                onClick = {},
                                enabled = false,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("⏳ بانتظار صديق...", color = TextSecondary, fontSize = 11.sp)
                            }
                        }

                        Button(
                            onClick = { showBotLevelPicker = true },
                            colors = ButtonDefaults.buttonColors(containerColor = WarningAmber),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("🤖 تحدي البوت ▼", color = Color.Black, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp)
                        }
                    }
                } else {
                    // Clean Segmented Difficulty Bar (Disappears immediately after tap!)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black.copy(alpha = 0.32f))
                            .padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("اختر مستوى ذكاء البوت (0 استهلاك سيرفر):", color = WarningAmber, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val levels = listOf(
                                Triple("EASY", "🟢 سهل", Color(0xFF00E676)),
                                Triple("MEDIUM", "🟡 متوسط", Color(0xFFFFD54F)),
                                Triple("PRO", "🔴 حريف", Color(0xFFFF5252))
                            )
                            levels.forEach { (lvlKey, lvlTitle, lvlColor) ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(lvlColor.copy(alpha = 0.2f))
                                        .border(1.dp, lvlColor, RoundedCornerShape(10.dp))
                                        .clickable {
                                            botDifficulty = lvlKey
                                            isLocalBotMode = true
                                            showBotLevelPicker = false
                                            val botLabel = when (lvlKey) {
                                                "EASY" -> "🤖 البوت (سهل)"
                                                "MEDIUM" -> "🤖 البوت (متوسط)"
                                                else -> "🤖 البوت (حريف)"
                                            }
                                            val myUid = currentUserId.ifBlank { p1Id }
                                            val myCleanName = currentUserName.replace(":", "").replace("|", "").replace(",", "").ifBlank { p1Name }
                                            localGameState = "[GAME_DOMINO]||$myUid:$myCleanName:${p1Hand.joinToString(",")}|BOT_$lvlKey:$botLabel:${p2Hand.joinToString(",")}|${boneyard.joinToString(",")}|$myUid|ACTIVE"
                                        }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(lvlTitle, color = lvlColor, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            } else {
                // STAGE 2: ACTIVE GAMEPLAY (Compact Score Header + Snake Felt Board + Player Upright Hand)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.Black.copy(alpha = 0.30f))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${if (turnUid == p1Id && status == "ACTIVE") "🟢 " else ""}$p1Name (${p1Hand.size} قطع)",
                        color = if (turnUid == p1Id) OnlineGreen else TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                    if (leftEnd != null && rightEnd != null) {
                        Text(
                            text = "الأطراف: [$leftEnd | $rightEnd]",
                            color = WarningAmber,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    } else {
                        Text("⚡ ضد ⚡", color = WarningAmber, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        text = "${if (turnUid == p2Id && status == "ACTIVE") "🟢 " else ""}$p2Name (${p2Hand.size} قطع)",
                        color = if (turnUid == p2Id) OnlineGreen else TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }

                // Multi-Row Snake Domino Felt Board (With Vertical Doubles & Engraved Pips)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = if (isExpandedModal) 260.dp else 135.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            Brush.radialGradient(
                                listOf(Color(0xFF0E3828), Color(0xFF061A12))
                            )
                        )
                        .border(1.2.dp, Color(0xFF26634B), RoundedCornerShape(14.dp))
                        .padding(10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (boardTiles.isEmpty()) {
                        Text(
                            text = "الطاولة جاهزة — المس أي ورقة من يدك لإنزالها على الطاولة!",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center
                        )
                    } else {
                        DominoSnakeBoardLayout(boardTiles = boardTiles)
                    }
                }

                // STAGE 3: WINNER BANNER OR PLAYER HAND
                if (status.startsWith("WIN:")) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(OnlineGreen.copy(alpha = 0.16f))
                            .border(1.dp, OnlineGreen, RoundedCornerShape(12.dp))
                            .padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "🏆 ${status.removePrefix("WIN:")}",
                            color = OnlineGreen,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                        Button(
                            onClick = {
                                val fresh = buildInitialDominoGameString(currentUserId.ifBlank { p1Id }, currentUserName.ifBlank { p1Name })
                                if (isLocalBotMode) {
                                    val sp = fresh.split("|")
                                    val freshP1 = sp[2]
                                    val freshP2Tiles = sp[3].substringAfterLast(":")
                                    val freshBone = sp[4]
                                    val botLabel = p2Name.ifBlank { "🤖 البوت" }
                                    localGameState = "[GAME_DOMINO]||$freshP1|$p2Id:$botLabel:$freshP2Tiles|$freshBone|$p1Id|ACTIVE"
                                } else {
                                    dispatchStateUpdate(fresh, forceServerSync = true)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = WarningAmber)
                        ) {
                            Text("🔄 جولة دومينو جديدة", color = Color.Black, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp)
                        }
                    }
                } else if (isPlayer1 || isPlayer2) {
                    val hasAnyPlayable = myHand.any { canPlayTileOnEnds(it, leftEnd, rightEnd) }

                    Text(
                        text = when {
                            isBotTurn -> "🤖 $p2Name يفكر في النقلة..."
                            isMyTurn && hasAnyPlayable -> "🎯 دورك الآن! اختر ورقة مضيئة للعب:"
                            isMyTurn && !hasAnyPlayable -> "⚠️ لا توجد ورقة مطابقة في يدك (${leftEnd} أو ${rightEnd})!"
                            else -> "⏳ بانتظار حركة الخصم..."
                        },
                        color = when {
                            isMyTurn && hasAnyPlayable -> OnlineGreen
                            isMyTurn && !hasAnyPlayable -> WarningAmber
                            else -> TextSecondary
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )

                    // Upright Domino Tiles in Player's Hand
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        items(myHand) { tile ->
                            val nums = tile.split("-")
                            val topVal = nums.getOrNull(0)?.toIntOrNull() ?: 0
                            val botVal = nums.getOrNull(1)?.toIntOrNull() ?: 0
                            val playable = isMyTurn && canPlayTileOnEnds(tile, leftEnd, rightEnd)
                            EngravedDominoTileCanvas(
                                firstVal = topVal,
                                secondVal = botVal,
                                isVertical = true, // Upright in player's hand!
                                isHighlighted = playable,
                                onClick = { if (playable) executeHumanPlay(tile) }
                            )
                        }
                    }

                    // Progressive Disclosure: Draw/Pass button ONLY appears when player has no playable tile!
                    if (isMyTurn && !hasAnyPlayable) {
                        Button(
                            onClick = { executeHumanDrawOrPass() },
                            colors = ButtonDefaults.buttonColors(containerColor = WarningAmber),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = if (boneyard.isNotEmpty()) "📥 سحب ورقة من السوق (المتبقي ${boneyard.size})"
                                else "⏭️ تمرير الدور / حسم قفل الطاولة",
                                color = Color.Black,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }

    RenderDominoTableContent(isExpandedModal = false)

    if (isFullScreenTable) {
        Dialog(
            onDismissRequest = { isFullScreenTable = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.92f))
                    .padding(14.dp),
                contentAlignment = Alignment.Center
            ) {
                RenderDominoTableContent(isExpandedModal = true)
            }
        }
    }
}

/**
 * Snake / U-Turn Multi-Row Domino Board Layout:
 * Wraps the domino chain every 4 tiles, alternating Left-to-Right and Right-to-Left rows
 * with U-turn indicators, and automatically stands Double tiles (e.g. 6-6) vertically!
 */
@Composable
private fun DominoSnakeBoardLayout(boardTiles: List<String>) {
    val rows = remember(boardTiles) { boardTiles.chunked(4) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        rows.forEachIndexed { rowIndex, rowTiles ->
            val isReversedRow = (rowIndex % 2 == 1)
            val orderedRow = if (isReversedRow) rowTiles.reversed() else rowTiles

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (isReversedRow && rowIndex < rows.lastIndex) {
                    Text("⤶", color = Color(0xFFFFD54F), fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                }

                orderedRow.forEach { t ->
                    val nums = t.split("-")
                    val rawA = nums.getOrNull(0)?.toIntOrNull() ?: 0
                    val rawB = nums.getOrNull(1)?.toIntOrNull() ?: 0
                    val isDouble = (rawA == rawB)

                    // On reversed (Right-to-Left) rows, flip horizontal tile halves so matching numbers visually touch!
                    val displayFirst = if (isReversedRow && !isDouble) rawB else rawA
                    val displaySecond = if (isReversedRow && !isDouble) rawA else rawB

                    EngravedDominoTileCanvas(
                        firstVal = displayFirst,
                        secondVal = displaySecond,
                        isVertical = isDouble, // Doubles stand vertically on the board!
                        isHighlighted = false,
                        onClick = {}
                    )
                }

                if (!isReversedRow && rowIndex < rows.lastIndex) {
                    Text("⤵", color = Color(0xFFFFD54F), fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                }
            }
        }
    }
}

/**
 * Real Engraved 3D Domino Tile with Authentic Circular Pips & Brass Center Spinner Pin.
 */
@Composable
private fun EngravedDominoTileCanvas(
    firstVal: Int,
    secondVal: Int,
    isVertical: Boolean,
    isHighlighted: Boolean,
    onClick: () -> Unit
) {
    val tileW = if (isVertical) 34.dp else 62.dp
    val tileH = if (isVertical) 62.dp else 34.dp

    Canvas(
        modifier = Modifier
            .size(width = tileW, height = tileH)
            .clip(RoundedCornerShape(7.dp))
            .clickable(enabled = isHighlighted) { onClick() }
    ) {
        // 1. Drop shadow / bevel base
        drawRoundRect(
            color = Color(0xFF1A252C),
            cornerRadius = CornerRadius(14f, 14f),
            size = size
        )

        // 2. Warm Ivory 3D Tile Surface
        val pad = 2f
        drawRoundRect(
            color = if (isHighlighted) Color(0xFFFFFEF5) else Color(0xFFF3EFE6),
            topLeft = Offset(pad, pad),
            size = Size(size.width - pad * 2, size.height - pad * 2),
            cornerRadius = CornerRadius(12f, 12f)
        )

        // 3. Outer Border (Glowing Neon Green when playable in hand)
        drawRoundRect(
            color = if (isHighlighted) Color(0xFF00E676) else Color(0xFF2C3E50),
            cornerRadius = CornerRadius(14f, 14f),
            size = size,
            style = Stroke(width = if (isHighlighted) 4.5f else 2f)
        )

        // 4. Center Divider Line & Golden Brass Spinner Pin
        if (isVertical) {
            val midY = size.height / 2f
            drawLine(
                color = Color(0xFF34495E),
                start = Offset(6f, midY),
                end = Offset(size.width - 6f, midY),
                strokeWidth = 2.4f
            )
            drawCircle(
                color = Color(0xFFD4AF37),
                radius = 2.6f,
                center = Offset(size.width / 2f, midY)
            )
            // Top & Bottom Halves Pips
            drawDominoHalfPips(
                value = firstVal,
                cx = size.width / 2f,
                cy = size.height * 0.25f,
                halfW = size.width,
                halfH = size.height / 2f,
                isVerticalTile = true
            )
            drawDominoHalfPips(
                value = secondVal,
                cx = size.width / 2f,
                cy = size.height * 0.75f,
                halfW = size.width,
                halfH = size.height / 2f,
                isVerticalTile = true
            )
        } else {
            val midX = size.width / 2f
            drawLine(
                color = Color(0xFF34495E),
                start = Offset(midX, 6f),
                end = Offset(midX, size.height - 6f),
                strokeWidth = 2.4f
            )
            drawCircle(
                color = Color(0xFFD4AF37),
                radius = 2.6f,
                center = Offset(midX, size.height / 2f)
            )
            // Left & Right Halves Pips
            drawDominoHalfPips(
                value = firstVal,
                cx = size.width * 0.25f,
                cy = size.height / 2f,
                halfW = size.width / 2f,
                halfH = size.height,
                isVerticalTile = false
            )
            drawDominoHalfPips(
                value = secondVal,
                cx = size.width * 0.75f,
                cy = size.height / 2f,
                halfW = size.width / 2f,
                halfH = size.height,
                isVerticalTile = false
            )
        }
    }
}

private fun DrawScope.drawDominoHalfPips(
    value: Int,
    cx: Float,
    cy: Float,
    halfW: Float,
    halfH: Float,
    isVerticalTile: Boolean
) {
    if (value <= 0) return

    // Classic colour-coded engraved pips for instant visual recognition
    val pipColor = when (value) {
        1 -> Color(0xFF1E272E)
        2 -> Color(0xFF0097E6)
        3 -> Color(0xFFE84118)
        4 -> Color(0xFF009432)
        5 -> Color(0xFF8C7AE6)
        6 -> Color(0xFFC23616)
        else -> Color(0xFF1E272E)
    }

    val dx = halfW * 0.27f
    val dy = halfH * 0.27f
    val r = (min(halfW, halfH) * 0.115f).coerceAtLeast(2.8f)

    fun pip(ox: Float, oy: Float) {
        drawCircle(color = pipColor, radius = r, center = Offset(cx + ox, cy + oy))
    }

    when (value) {
        1 -> pip(0f, 0f)
        2 -> {
            pip(-dx, -dy)
            pip(dx, dy)
        }
        3 -> {
            pip(-dx, -dy)
            pip(0f, 0f)
            pip(dx, dy)
        }
        4 -> {
            pip(-dx, -dy)
            pip(dx, -dy)
            pip(-dx, dy)
            pip(dx, dy)
        }
        5 -> {
            pip(-dx, -dy)
            pip(dx, -dy)
            pip(0f, 0f)
            pip(-dx, dy)
            pip(dx, dy)
        }
        6 -> {
            if (isVerticalTile) {
                // 2 columns x 3 rows
                pip(-dx, -dy)
                pip(dx, -dy)
                pip(-dx, 0f)
                pip(dx, 0f)
                pip(-dx, dy)
                pip(dx, dy)
            } else {
                // 3 columns x 2 rows
                pip(-dx, -dy)
                pip(0f, -dy)
                pip(dx, -dy)
                pip(-dx, dy)
                pip(0f, dy)
                pip(dx, dy)
            }
        }
    }
}

fun buildInitialDominoGameString(hostId: String, hostName: String): String {
    val cleanHost = hostName.replace(":", "").replace("|", "").replace(",", "").ifBlank { "اللاعب 1" }
    val allTiles = mutableListOf<String>()
    for (i in 0..6) {
        for (j in i..6) {
            allTiles.add("$i-$j")
        }
    }
    allTiles.shuffle()
    val p1Hand = allTiles.take(7).joinToString(",")
    val p2Hand = allTiles.drop(7).take(7).joinToString(",")
    val boneyard = allTiles.drop(14).joinToString(",")
    return "[GAME_DOMINO]||$hostId:$cleanHost:$p1Hand|?:?:$p2Hand|$boneyard|$hostId|WAITING_P2"
}
