package com.virusx.app.ui.stories

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.virusx.app.data.repository.StoryWithDetails
import com.virusx.app.ui.theme.DangerRed
import com.virusx.app.ui.theme.OnlineGreen
import com.virusx.app.ui.theme.SentMessageGradient
import com.virusx.app.ui.theme.TextMuted
import com.virusx.app.ui.theme.TextPrimary
import com.virusx.app.ui.theme.TextSecondary
import com.virusx.app.ui.theme.VirusXBackground
import com.virusx.app.ui.theme.VirusXGlassBorder
import com.virusx.app.ui.theme.VirusXPrimary
import com.virusx.app.ui.theme.VirusXSecondaryWhisper
import com.virusx.app.ui.theme.VirusXSurface
import com.virusx.app.ui.theme.VirusXSurfaceElevated
import com.virusx.app.ui.theme.WhisperMessageGradient
import java.time.Instant
import java.time.temporal.ChronoUnit

@Composable
fun StoriesScreen(
    onBack: () -> Unit,
    viewModel: StoriesViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        containerColor = VirusXBackground
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = TextPrimary)
                    }
                    Text("الحالات التفاعلية (24 ساعة)", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                }

                Button(
                    onClick = viewModel::openCreateDialog,
                    colors = ButtonDefaults.buttonColors(containerColor = VirusXPrimary),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("حالة جديدة")
                }
            }

            state.statusMessage?.let { msg ->
                Text(
                    text = msg,
                    color = OnlineGreen,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(VirusXSurface)
                        .clickable { viewModel.clearMessage() }
                        .padding(10.dp)
                )
            }

            if (state.isLoading && state.stories.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = VirusXPrimary)
                }
            } else if (state.stories.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "لا توجد حالات نشطة حالياً.\nاضغط على \"حالة جديدة\" لمشاركة نص، صورة، تصويت، أو سؤال مجهول!",
                        color = TextMuted,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    itemsIndexed(state.stories) { index, item ->
                        val isSelected = index == state.selectedIndex
                        val name = item.author?.displayName ?: "عضو"
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable { viewModel.selectStoryIndex(index) }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(58.dp)
                                    .clip(CircleShape)
                                    .border(
                                        width = if (isSelected) 2.5.dp else 1.dp,
                                        color = if (isSelected) VirusXSecondaryWhisper else VirusXGlassBorder,
                                        shape = CircleShape
                                    )
                                    .background(SentMessageGradient),
                                contentAlignment = Alignment.Center
                            ) {
                                if (!item.author?.avatarUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = item.author?.avatarUrl,
                                        contentDescription = name,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Text(
                                        text = name.take(1).uppercase(),
                                        color = TextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 20.sp
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = name.take(10),
                                color = if (isSelected) VirusXSecondaryWhisper else TextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                val activeItem = state.stories.getOrNull(state.selectedIndex)
                if (activeItem != null) {
                    ActiveStoryViewerCard(
                        item = activeItem,
                        isMine = activeItem.story.userId == state.currentUserId,
                        onVote = { pollId, idx -> viewModel.voteOnPoll(pollId, idx) },
                        onSendAsk = { storyId, q -> viewModel.submitAnonymousQuestion(storyId, q) },
                        onDelete = { viewModel.deleteStory(activeItem.story.id) }
                    )
                }
            }
        }
    }

    if (state.showCreateDialog) {
        CreateInteractiveStoryDialog(
            onDismiss = viewModel::closeCreateDialog,
            onPublishText = viewModel::publishTextStory,
            onPublishMedia = viewModel::publishMediaStory,
            onPublishPoll = viewModel::publishPollStory,
            onPublishAsk = viewModel::publishAskStory,
            onPublishCountdown = viewModel::publishCountdownStory
        )
    }
}

@Composable
private fun ActiveStoryViewerCard(
    item: StoryWithDetails,
    isMine: Boolean,
    onVote: (String, Int) -> Unit,
    onSendAsk: (String, String) -> Unit,
    onDelete: () -> Unit
) {
    val bgColor = remember(item.story.backgroundColor) {
        runCatching {
            Color(android.graphics.Color.parseColor(item.story.backgroundColor ?: "#1E1B4B"))
        }.getOrElse { VirusXSurface }
    }

    var askInput by remember(item.story.id) { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .clip(RoundedCornerShape(24.dp))
            .background(bgColor)
            .border(1.dp, VirusXGlassBorder, RoundedCornerShape(24.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = item.author?.displayName ?: "عضو VirusX",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Text(
                    text = "@${item.author?.username ?: "user"}",
                    color = VirusXSecondaryWhisper,
                    fontSize = 12.sp
                )
            }

            if (isMine) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "حذف الحالة", tint = DangerRed)
                }
            }
        }

        when (item.story.storyType) {
            "media" -> {
                if (!item.story.mediaUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = item.story.mediaUrl,
                        contentDescription = "صورة الحالة",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(320.dp)
                            .clip(RoundedCornerShape(16.dp))
                    )
                }
                if (!item.story.textContent.isNullOrBlank()) {
                    Text(
                        text = item.story.textContent,
                        color = TextPrimary,
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            "poll" -> {
                val poll = item.poll
                Text(
                    text = "📊 ${poll?.question ?: item.story.textContent.orEmpty()}",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
                if (poll != null) {
                    val totalVotes = item.pollVotes.size.coerceAtLeast(1)
                    poll.options.forEachIndexed { idx, optionLabel ->
                        val count = item.pollVotes.count { it.optionIndex == idx }
                        val ratio = if (item.pollVotes.isEmpty()) 0f else count.toFloat() / totalVotes

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.Black.copy(alpha = 0.35f))
                                .border(1.dp, VirusXGlassBorder, RoundedCornerShape(14.dp))
                                .clickable { onVote(poll.id, idx) }
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(optionLabel, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                                Text("${(ratio * 100).toInt()}% ($count)", color = VirusXSecondaryWhisper, fontSize = 12.sp)
                            }
                            LinearProgressIndicator(
                                progress = { ratio },
                                color = VirusXSecondaryWhisper,
                                trackColor = VirusXSurfaceElevated,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(CircleShape)
                            )
                        }
                    }
                }
            }

            "ask" -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(WhisperMessageGradient)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "🕵️ اسألني أي شيء (مجهول الهوية):",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Text(
                        text = item.story.textContent ?: "اكتب سؤالك بسرية تامة...",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )

                    if (!isMine) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = askInput,
                                onValueChange = { askInput = it },
                                placeholder = { Text("اكتب سؤالك المجهول هنا...") },
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(
                                onClick = {
                                    if (askInput.isNotBlank()) {
                                        onSendAsk(item.story.id, askInput)
                                        askInput = ""
                                    }
                                },
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(VirusXPrimary)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "إرسال", tint = TextPrimary)
                            }
                        }
                    }
                }

                if (isMine) {
                    Text(
                        text = "الأسئلة الواردة لك (${item.anonymousQuestions.size}):",
                        color = VirusXSecondaryWhisper,
                        fontWeight = FontWeight.Bold
                    )
                    item.anonymousQuestions.forEach { q ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.Black.copy(alpha = 0.35f))
                                .padding(12.dp)
                        ) {
                            Text("❓ ${q.questionText}", color = TextPrimary, fontSize = 14.sp)
                        }
                    }
                }
            }

            "countdown" -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color.Black.copy(alpha = 0.35f))
                        .border(1.dp, VirusXSecondaryWhisper, RoundedCornerShape(18.dp))
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Timer, contentDescription = null, tint = VirusXSecondaryWhisper, modifier = Modifier.size(36.dp))
                    Text(
                        text = item.countdown?.title ?: item.story.textContent.orEmpty(),
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                    Text(
                        text = "الموعد المستهدف: ${item.countdown?.targetDatetime ?: ""}",
                        color = VirusXSecondaryWhisper,
                        fontSize = 13.sp
                    )
                }
            }

            else -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = item.story.textContent.orEmpty(),
                        color = TextPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun CreateInteractiveStoryDialog(
    onDismiss: () -> Unit,
    onPublishText: (String, String) -> Unit,
    onPublishMedia: (Uri, String?) -> Unit,
    onPublishPoll: (String, String, String, String) -> Unit,
    onPublishAsk: (String, String) -> Unit,
    onPublishCountdown: (String, String, String) -> Unit
) {
    var selectedType by remember { mutableStateOf("text") }
    var textValue by remember { mutableStateOf("") }
    var option1 by remember { mutableStateOf("") }
    var option2 by remember { mutableStateOf("") }
    var selectedBg by remember { mutableStateOf("#1E1B4B") }
    var pickedImageUri by remember { mutableStateOf<Uri?>(null) }

    val imageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        pickedImageUri = uri
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VirusXSurface,
        title = { Text("نشر حالة تفاعلية جديدة", color = TextPrimary) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val types = listOf(
                        "text" to "نص",
                        "media" to "صورة",
                        "poll" to "تصويت",
                        "ask" to "سؤال مجهول",
                        "countdown" to "عداد"
                    )
                    itemsIndexed(types) { _, (key, label) ->
                        FilterChip(
                            selected = selectedType == key,
                            onClick = { selectedType = key },
                            label = { Text(label, fontSize = 12.sp) }
                        )
                    }
                }

                when (selectedType) {
                    "media" -> {
                        Button(
                            onClick = { imageLauncher.launch("image/*") },
                            colors = ButtonDefaults.buttonColors(containerColor = VirusXSurfaceElevated),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Image, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (pickedImageUri != null) "تم اختيار الصورة ✓" else "اختر صورة من المعرض")
                        }
                        OutlinedTextField(
                            value = textValue,
                            onValueChange = { textValue = it },
                            label = { Text("تعليق على الصورة (اختياري)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    "poll" -> {
                        OutlinedTextField(
                            value = textValue,
                            onValueChange = { textValue = it },
                            label = { Text("سؤال التصويت") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = option1,
                            onValueChange = { option1 = it },
                            label = { Text("الخيار الأول") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = option2,
                            onValueChange = { option2 = it },
                            label = { Text("الخيار الثاني") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    "ask" -> {
                        OutlinedTextField(
                            value = textValue,
                            onValueChange = { textValue = it },
                            label = { Text("عنوان صندوق الأسئلة (مثال: اسألني أي سؤال بصراحة)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    "countdown" -> {
                        OutlinedTextField(
                            value = textValue,
                            onValueChange = { textValue = it },
                            label = { Text("عنوان الحدث (مثال: امتحان الخوارزميات)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = "سيتم ضبط العداد تلقائياً لمدة 24 ساعة من الآن.",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                    else -> {
                        OutlinedTextField(
                            value = textValue,
                            onValueChange = { textValue = it },
                            label = { Text("اكتب نص الحالة...") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    when (selectedType) {
                        "media" -> pickedImageUri?.let { onPublishMedia(it, textValue) }
                        "poll" -> onPublishPoll(textValue, option1, option2, selectedBg)
                        "ask" -> onPublishAsk(textValue, selectedBg)
                        "countdown" -> {
                            val targetIso = Instant.now().plus(24, ChronoUnit.HOURS).toString()
                            onPublishCountdown(textValue, targetIso, selectedBg)
                        }
                        else -> onPublishText(textValue, selectedBg)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = VirusXPrimary)
            ) {
                Text("نشر الآن")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء", color = TextSecondary)
            }
        }
    )
}
