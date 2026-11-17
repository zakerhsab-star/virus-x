package com.virusx.app.ui.chat

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.virusx.app.ui.theme.*
import com.virusx.app.util.VirusXEffectsAndNotifications
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlinx.coroutines.delay

// ============================================================================
// 1. LIVE ROULETTE WHEEL ENGINE (Canvas 2D + CubicBezier Physics Spin)
// Format: [EVENT_ROULETTE]|title|p1:name1,p2:name2|status(LOBBY/SPIN)|targetAngle|winnerName
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

        // Animated 2D Roulette Wheel Canvas
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

                // Outer golden rim
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
                    // Draw label inside slice
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

                // Center Hub
                drawCircle(color = Color(0xFF0A0D12), radius = 24f, center = center)
                drawCircle(color = Color(0xFFFFD54F), radius = 24f, center = center, style = Stroke(width = 4f))
            }

            // Top Pointer Needle
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
                        // Pointer is at top (270 deg in standard canvas coords)
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
// Format: [GAME_IMPOSTER]|category|secretWord|imposterUid|p1:name1,p2:name2|status(LOBBY/PLAYING/VOTED)|votes(voterId>targetId)
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
// Format: [GAME_BOMB]|letters|holderName|deadlineEpochSec|scoreCount|status(ACTIVE/BOOM)|usedWordsCsv
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
// Format: [EVENT_HOTSEAT]|accusedName|currentQuestion|askedBy|answerText|status(WAITING_Q/WAITING_A/ESCAPED)
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
// 5. PRO DOMINOES TABLE (🁣 طاولة الدومينو الاحترافية بـ Canvas)
// Format: [GAME_DOMINO]|boardTilesCsv(6-6,6-4)|p1Id:p1Name:handCsv|p2Id:p2Name:handCsv|boneyardCsv|turnUid|status
// ============================================================================

@Composable
fun InChatDominoTableCard(
    rawState: String,
    currentUserId: String,
    currentUserName: String,
    onUpdateState: (String) -> Unit
) {
    val parts = rawState.split("|")
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

    val isPlayer1 = currentUserId == p1Id
    val isPlayer2 = currentUserId == p2Id
    val myHand = when {
        isPlayer1 -> p1Hand
        isPlayer2 -> p2Hand
        else -> emptyList()
    }
    val isMyTurn = (status == "ACTIVE") && (currentUserId == turnUid)

    val leftEnd: Int? = remember(boardTiles) {
        boardTiles.firstOrNull()?.split("-")?.firstOrNull()?.toIntOrNull()
    }
    val rightEnd: Int? = remember(boardTiles) {
        boardTiles.lastOrNull()?.split("-")?.getOrNull(1)?.toIntOrNull()
    }

    fun canPlayTile(tile: String): Boolean {
        if (boardTiles.isEmpty()) return true
        val ab = tile.split("-")
        val a = ab.getOrNull(0)?.toIntOrNull() ?: return false
        val b = ab.getOrNull(1)?.toIntOrNull() ?: return false
        return a == leftEnd || b == leftEnd || a == rightEnd || b == rightEnd
    }

    fun playTileOnBoard(tile: String) {
        if (!isMyTurn) return
        val ab = tile.split("-")
        val a = ab[0].toInt()
        val b = ab[1].toInt()

        val nextBoard = if (boardTiles.isEmpty()) {
            listOf(tile)
        } else if (b == leftEnd) {
            listOf("$a-$b") + boardTiles
        } else if (a == leftEnd) {
            listOf("$b-$a") + boardTiles
        } else if (a == rightEnd) {
            boardTiles + listOf("$a-$b")
        } else if (b == rightEnd) {
            boardTiles + listOf("$b-$a")
        } else {
            return
        }

        val nextMyHand = myHand.toMutableList().also { it.remove(tile) }
        val nextP1Hand = if (isPlayer1) nextMyHand else p1Hand
        val nextP2Hand = if (isPlayer2) nextMyHand else p2Hand

        val nextStatus = when {
            nextP1Hand.isEmpty() -> {
                VirusXEffectsAndNotifications.playSoundEffect("VICTORY")
                "WIN:$p1Name (دومينو!)"
            }
            nextP2Hand.isEmpty() -> {
                VirusXEffectsAndNotifications.playSoundEffect("VICTORY")
                "WIN:$p2Name (دومينو!)"
            }
            else -> "ACTIVE"
        }
        val nextTurn = if (isPlayer1) p2Id else p1Id
        onUpdateState(
            "[GAME_DOMINO]|${nextBoard.joinToString(",")}|$p1Id:$p1Name:${nextP1Hand.joinToString(",")}|$p2Id:$p2Name:${nextP2Hand.joinToString(",")}|${boneyard.joinToString(",")}|$nextTurn|$nextStatus"
        )
    }

    fun drawFromBoneyardOrPass() {
        if (!isMyTurn) return
        if (boneyard.isNotEmpty()) {
            val drawn = boneyard.first()
            val nextBoneyard = boneyard.drop(1)
            val nextMyHand = myHand + drawn
            val nextP1Hand = if (isPlayer1) nextMyHand else p1Hand
            val nextP2Hand = if (isPlayer2) nextMyHand else p2Hand
            onUpdateState(
                "[GAME_DOMINO]|${boardTiles.joinToString(",")}|$p1Id:$p1Name:${nextP1Hand.joinToString(",")}|$p2Id:$p2Name:${nextP2Hand.joinToString(",")}|${nextBoneyard.joinToString(",")}|$turnUid|ACTIVE"
            )
        } else {
            // Pass turn if boneyard is empty
            val nextTurn = if (isPlayer1) p2Id else p1Id
            onUpdateState(
                "[GAME_DOMINO]|${boardTiles.joinToString(",")}|$p1Id:$p1Name:${p1Hand.joinToString(",")}|$p2Id:$p2Name:${p2Hand.joinToString(",")}|${boneyard.joinToString(",")}|$nextTurn|ACTIVE"
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0D2E22), Color(0xFF071A13))
                )
            )
            .border(1.5.dp, Color(0xFFD4AF37), RoundedCornerShape(16.dp))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("🁣 طاولة الدومينو الحية", color = Color(0xFFFFD54F), fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
            Text("السوق: ${boneyard.size} قطعة", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }

        if (status == "WAITING_P2") {
            Text("المؤسس: $p1Name (7 قطع) • بانتظار خصم للجلوس على الطاولة!", color = TextPrimary, fontSize = 12.sp, textAlign = TextAlign.Center)
            if (!isPlayer1) {
                Button(
                    onClick = {
                        val cleanName = currentUserName.replace(":", "").replace("|", "").replace(",", "").ifBlank { "اللاعب 2" }
                        onUpdateState(
                            "[GAME_DOMINO]||$p1Id:$p1Name:${p1Hand.joinToString(",")}|$currentUserId:$cleanName:${p2Hand.joinToString(",")}|${boneyard.joinToString(",")}|$p1Id|ACTIVE"
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = OnlineGreen)
                ) {
                    Text("🪑 الجلوس على الطاولة وبدء الجيم", color = Color.Black, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                }
            }
        } else {
            // Players Scoreboard Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Black.copy(alpha = 0.28f))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${if (turnUid == p1Id && status == "ACTIVE") "🟢 " else ""}$p1Name (${p1Hand.size})",
                    color = if (turnUid == p1Id) OnlineGreen else TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
                Text("⚡ ضد ⚡", color = WarningAmber, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = "${if (turnUid == p2Id && status == "ACTIVE") "🟢 " else ""}$p2Name (${p2Hand.size})",
                    color = if (turnUid == p2Id) OnlineGreen else TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
            }

            // Green Felt Domino Board Chain
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 78.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF06130D))
                    .border(1.dp, Color(0xFF1F4E3B), RoundedCornerShape(12.dp))
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                if (boardTiles.isEmpty()) {
                    Text("الطاولة جاهزة — العب أول بلاطة للبدء!", color = TextSecondary, fontSize = 11.sp)
                } else {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        boardTiles.forEach { t ->
                            val nums = t.split("-")
                            val topN = nums.getOrNull(0)?.toIntOrNull() ?: 0
                            val botN = nums.getOrNull(1)?.toIntOrNull() ?: 0
                            DominoTileCanvas(
                                leftVal = topN,
                                rightVal = botN,
                                isPlayable = false,
                                onClick = {}
                            )
                        }
                    }
                }
            }

            if (status.startsWith("WIN:")) {
                Text(
                    text = "🏆 ${status.removePrefix("WIN:")}",
                    color = OnlineGreen,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp
                )
            } else if (isPlayer1 || isPlayer2) {
                Text(
                    text = if (isMyTurn) "🎯 دورك الآن! اختر قطعة مناسبة للعب (${leftEnd ?: "أي"} أو ${rightEnd ?: "أي"}):"
                    else "⏳ بانتظار حركة الخصم...",
                    color = if (isMyTurn) WarningAmber else TextSecondary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    items(myHand) { tile ->
                        val nums = tile.split("-")
                        val l = nums.getOrNull(0)?.toIntOrNull() ?: 0
                        val r = nums.getOrNull(1)?.toIntOrNull() ?: 0
                        val playable = isMyTurn && canPlayTile(tile)
                        DominoTileCanvas(
                            leftVal = l,
                            rightVal = r,
                            isPlayable = playable,
                            onClick = { if (playable) playTileOnBoard(tile) }
                        )
                    }
                }

                if (isMyTurn && myHand.none { canPlayTile(it) }) {
                    Button(
                        onClick = { drawFromBoneyardOrPass() },
                        colors = ButtonDefaults.buttonColors(containerColor = WarningAmber)
                    ) {
                        Text(
                            text = if (boneyard.isNotEmpty()) "📥 سحب بلاطة من السوق (${boneyard.size})" else "⏭️ تمرير الدور (قفل)",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DominoTileCanvas(
    leftVal: Int,
    rightVal: Int,
    isPlayable: Boolean,
    onClick: () -> Unit
) {
    Canvas(
        modifier = Modifier
            .size(width = 62.dp, height = 34.dp)
            .clip(RoundedCornerShape(6.dp))
            .clickable(enabled = isPlayable) { onClick() }
    ) {
        // Ivory Tile Body
        drawRoundRect(
            color = if (isPlayable) Color(0xFFFFFDE7) else Color(0xFFEAE6DF),
            cornerRadius = CornerRadius(12f, 12f),
            size = size
        )
        drawRoundRect(
            color = if (isPlayable) Color(0xFF00E676) else Color(0xFF2C3E50),
            cornerRadius = CornerRadius(12f, 12f),
            size = size,
            style = Stroke(width = if (isPlayable) 4f else 2f)
        )
        // Center Divider Line
        drawLine(
            color = Color(0xFF2C3E50),
            start = Offset(size.width / 2f, 4f),
            end = Offset(size.width / 2f, size.height - 4f),
            strokeWidth = 2.5f
        )

        // Draw pips or crisp numbers on each half
        drawContext.canvas.nativeCanvas.apply {
            val paint = Paint().apply {
                color = android.graphics.Color.rgb(20, 28, 38)
                textSize = 32f
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
            }
            drawText(leftVal.toString(), size.width * 0.25f, size.height * 0.68f, paint)
            drawText(rightVal.toString(), size.width * 0.75f, size.height * 0.68f, paint)
        }
    }
}

// ============================================================================
// HELPER TO GENERATE SHUFFLED DOMINO INITIAL STATE
// ============================================================================
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
