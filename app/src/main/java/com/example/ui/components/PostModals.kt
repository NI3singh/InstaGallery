// InstaGallery v1.0
package com.example.ui.components

import android.content.Context
import android.media.ExifInterface
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FilledExtendedIcons
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.OutlinedExtendedIcons
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.local.entity.MediaEntity
import com.example.data.local.entity.PersonEntity
import com.example.data.repository.GalleryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Detects machine-generated camera, WhatsApp, screenshot, or watermark-tool filenames
 * so the feed stays clean unless the user adds a personal memory note.
 */
fun isMachineGeneratedFilename(displayName: String): Boolean {
    val stem = displayName.substringBeforeLast('.').trim()
    if (stem.isEmpty()) return true
    val machinePattern = Regex(
        "^(IMG|DSC|PXL|VID|Screenshot|WA|received|watermark|instagallery|temp|image|photo|pic|snap|frame|clip|media|doc|file| burst|pxl|dji|gopro|mvimg)[_\\-0-9a-zA-Z]*$",
        RegexOption.IGNORE_CASE
    )
    if (machinePattern.matches(stem)) return true
    val digitCount = stem.count { it.isDigit() }
    if (digitCount >= 5 && digitCount.toFloat() / stem.length >= 0.35f) return true
    if (stem.contains("watermark-removed", ignoreCase = true) ||
        stem.contains("whatsapp", ignoreCase = true) ||
        stem.contains("screenshot", ignoreCase = true) ||
        stem.contains("instacard_", ignoreCase = true)
    ) {
        return true
    }
    val hexAndHyphenCount = stem.count { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' || it == '-' || it == '_' }
    if (stem.length >= 12 && hexAndHyphenCount == stem.length && digitCount >= 3) return true
    return false
}

/**
 * Native Instagram Dark Mode CaptionEditorSheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptionEditorSheet(
    media: MediaEntity,
    primaryAuthorName: String,
    formattedDate: String,
    onSaveCaption: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var captionInput by remember(media.mediaId, media.customCaption) {
        mutableStateOf(media.customCaption ?: "")
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF121214),
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = Color(0xFF3A3A3E))
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 18.dp)
                .testTag("caption_editor_sheet")
        ) {
            // Header Row: Cancel | Caption | Save
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "Cancel",
                    fontSize = 15.sp,
                    color = Color(0xFFA0A0A5),
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .clickable { onDismiss() }
                        .padding(vertical = 6.dp, horizontal = 4.dp)
                        .testTag("cancel_caption_button")
                )

                Text(
                    text = "Caption",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center)
                )

                Text(
                    text = "Save",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0095F6),
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .clickable {
                            onSaveCaption(captionInput.trim().takeIf { it.isNotEmpty() })
                            onDismiss()
                        }
                        .padding(vertical = 6.dp, horizontal = 4.dp)
                        .testTag("save_caption_button")
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            HorizontalDivider(
                color = Color(0xFF262629),
                thickness = 0.5.dp
            )

            // Clean Editor Area
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    AsyncImage(
                        model = media.uri,
                        contentDescription = media.displayName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .size(48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF1C1C20))
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    TextField(
                        value = captionInput,
                        onValueChange = { captionInput = it },
                        placeholder = {
                            Text(
                                text = "Write a caption...",
                                fontSize = 15.sp,
                                color = Color(0xFF6E6E73)
                            )
                        },
                        textStyle = TextStyle(
                            fontSize = 15.sp,
                            color = Color.White,
                            lineHeight = 20.sp
                        ),
                        minLines = 3,
                        maxLines = 6,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                            cursorColor = Color(0xFF0095F6),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("caption_text_field")
                    )
                }

                if (!media.customCaption.isNullOrBlank() || captionInput.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Clear caption",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFFF453A),
                        modifier = Modifier
                            .clickable {
                                captionInput = ""
                                onSaveCaption(null)
                                onDismiss()
                            }
                            .padding(vertical = 4.dp)
                            .testTag("clear_caption_button")
                    )
                }
            }
        }
    }
}

data class OfflineExifMetadata(
    val deviceModel: String?,
    val capturedDateText: String,
    val exposureSpecs: List<String>,
    val footerLine: String
)

private suspend fun loadOfflineExifMetadata(
    context: Context,
    media: MediaEntity
): OfflineExifMetadata = withContext(Dispatchers.IO) {
    val uri = runCatching { Uri.parse(media.uri) }.getOrNull()

    var exifW = media.width
    var exifH = media.height
    var cameraDevice: String? = null
    val exposureParts = mutableListOf<String>()
    var sizeStr: String? = null

    if (uri != null) {
        runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.SIZE),
                null,
                null,
                null
            )?.use { cursor ->
                val sizeCol = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeCol >= 0 && cursor.moveToFirst() && !cursor.isNull(sizeCol)) {
                    val bytes = cursor.getLong(sizeCol)
                    if (bytes > 0L) {
                        sizeStr = if (bytes >= 1024L * 1024L) {
                            String.format(Locale.US, "%.2f MB", bytes / (1024.0 * 1024.0))
                        } else {
                            String.format(Locale.US, "%d KB", (bytes / 1024L).coerceAtLeast(1L))
                        }
                    }
                }
            }
        }

        runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)

                val w = exif.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, 0)
                val l = exif.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, 0)
                if (w > 0 && l > 0) {
                    exifW = w
                    exifH = l
                }

                val make = exif.getAttribute(ExifInterface.TAG_MAKE)?.trim()
                val model = exif.getAttribute(ExifInterface.TAG_MODEL)?.trim()
                cameraDevice = when {
                    !make.isNullOrBlank() && !model.isNullOrBlank() -> {
                        if (model.startsWith(make, ignoreCase = true)) model else "$make $model"
                    }
                    !model.isNullOrBlank() -> model
                    !make.isNullOrBlank() -> make
                    else -> null
                }

                val focal35 = exif.getAttributeInt(ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM, 0)
                if (focal35 > 0) {
                    exposureParts.add("${focal35}mm")
                } else {
                    val focalRaw = exif.getAttributeDouble(ExifInterface.TAG_FOCAL_LENGTH, 0.0)
                    if (focalRaw > 0.0) {
                        val formattedFocal = if (focalRaw % 1.0 == 0.0) {
                            "${focalRaw.toInt()}mm"
                        } else {
                            String.format(Locale.US, "%.1fmm", focalRaw)
                        }
                        exposureParts.add(formattedFocal)
                    }
                }

                val fNum = exif.getAttribute(ExifInterface.TAG_F_NUMBER)
                if (!fNum.isNullOrBlank()) {
                    val fVal = fNum.split("/").let { parts ->
                        if (parts.size == 2) {
                            val n = parts[0].toDoubleOrNull()
                            val d = parts[1].toDoubleOrNull()
                            if (n != null && d != null && d != 0.0) n / d else null
                        } else fNum.toDoubleOrNull()
                    }
                    if (fVal != null && fVal > 0.0) {
                        exposureParts.add(String.format(Locale.US, "f/%.1f", fVal))
                    }
                }

                val expTime = exif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME)?.toDoubleOrNull()
                if (expTime != null && expTime > 0.0) {
                    val shutter = if (expTime < 1.0) {
                        "1/${(1.0 / expTime).roundToInt().coerceAtLeast(1)}s"
                    } else {
                        String.format(Locale.US, "%.1fs", expTime)
                    }
                    exposureParts.add(shutter)
                }

                val iso = exif.getAttribute("PhotographicSensitivity")
                    ?: exif.getAttribute(ExifInterface.TAG_ISO_SPEED_RATINGS)
                if (!iso.isNullOrBlank()) {
                    exposureParts.add("ISO ${iso.trim()}")
                }
            }
        }
    }

    val mp = if (exifW > 0 && exifH > 0) {
        val megapixels = (exifW.toLong() * exifH.toLong()) / 1_000_000.0
        String.format(Locale.US, "%.1f MP", megapixels)
    } else null

    val footerSegments = mutableListOf<String>()
    if (exifW > 0 && exifH > 0) {
        footerSegments.add("${exifW} × ${exifH}")
    }
    if (mp != null) {
        footerSegments.add(mp)
    }
    if (!sizeStr.isNullOrBlank()) {
        footerSegments.add(sizeStr!!)
    }
    footerSegments.add(media.displayName)

    val captureDateSdf = SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault())
    val capturedDateText = captureDateSdf.format(Date(media.dateAddedSeconds * 1000L))

    OfflineExifMetadata(
        deviceModel = cameraDevice,
        capturedDateText = capturedDateText,
        exposureSpecs = exposureParts,
        footerLine = footerSegments.joinToString("  •  ")
    )
}

/**
 * Native Instagram Dark Mode PostInspectorSheet (Three-Dot `⋮` Menu).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PostInspectorSheet(
    media: MediaEntity,
    authors: List<PersonEntity>,
    allPeople: List<PersonEntity>,
    formattedDate: String,
    onOpenInstaCardStudio: () -> Unit,
    onOpenCaptionEditor: () -> Unit,
    onOpenHeartThemePicker: () -> Unit = {},
    onShareOriginal: () -> Unit,
    onAuthorClick: (Long) -> Unit,
    onTagPerson: ((Long, Long) -> Unit)?,
    onUntagPerson: ((Long, Long) -> Unit)?,
    onToggleBookmark: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val repository = remember(context) { GalleryRepository(context) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var exifInfo by remember(media.mediaId) { mutableStateOf<OfflineExifMetadata?>(null) }

    val taggedIds = remember(authors) { authors.map { it.personId }.toSet() }
    val orderedPeople = remember(authors, allPeople, taggedIds) {
        val untagged = allPeople.filter { !taggedIds.contains(it.personId) }
        (authors + untagged).distinctBy { it.personId }
    }

    LaunchedEffect(media.mediaId) {
        exifInfo = loadOfflineExifMetadata(context, media)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF121214),
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = Color(0xFF3A3A3E))
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp)
                .testTag("post_inspector_sheet")
        ) {
            // 1. Top Grouped Action Strip (padding(horizontal = 16.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF1C1C20))
                    .padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                NativeActionStripItem(
                    icon = if (media.isBookmarked) FilledExtendedIcons.Bookmark else OutlinedExtendedIcons.BookmarkBorder,
                    label = if (media.isBookmarked) "Saved" else "Save",
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        if (onToggleBookmark != null) {
                            onToggleBookmark()
                        } else {
                            scope.launch {
                                repository.toggleBookmark(media)
                            }
                        }
                        onDismiss()
                    },
                    modifier = Modifier.testTag("inspector_save_action")
                )

                NativeActionStripItem(
                    icon = SnapshotStudioIcon,
                    label = "InstaCard",
                    onClick = {
                        onDismiss()
                        onOpenInstaCardStudio()
                    },
                    modifier = Modifier.testTag("inspector_instacard_action")
                )

                NativeActionStripItem(
                    icon = OutlinedExtendedIcons.ChatBubbleOutline,
                    label = "Caption",
                    onClick = {
                        onDismiss()
                        onOpenCaptionEditor()
                    },
                    modifier = Modifier.testTag("inspector_caption_action")
                )

                NativeActionStripItem(
                    icon = OutlinedExtendedIcons.Send,
                    label = "Share",
                    onClick = {
                        onDismiss()
                        onShareOriginal()
                    },
                    modifier = Modifier.testTag("inspector_share_action")
                )
            }

            // 2. Visual People Tagging Strip (padding(top = 18.dp))
            if (orderedPeople.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 18.dp)
                ) {
                    Text(
                        text = "People in this photo",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF8E8E93),
                        modifier = Modifier.padding(horizontal = 18.dp)
                    )

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(
                            items = orderedPeople,
                            key = { it.personId }
                        ) { person ->
                            val isTagged = taggedIds.contains(person.personId)
                            val cleanName = person.name.removePrefix("@")

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .width(64.dp)
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        if (isTagged) {
                                            onUntagPerson?.invoke(media.mediaId, person.personId)
                                        } else {
                                            onTagPerson?.invoke(media.mediaId, person.personId)
                                        }
                                    }
                                    .testTag("inspector_person_avatar_${person.personId}")
                            ) {
                                Box(
                                    modifier = Modifier.size(56.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(54.dp)
                                            .clip(CircleShape)
                                            .border(
                                                width = if (isTagged) 2.dp else 1.dp,
                                                color = if (isTagged) Color(0xFF0095F6) else Color(0xFF2C2C30),
                                                shape = CircleShape
                                            )
                                            .padding(if (isTagged) 3.dp else 2.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF1C1C20)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        val avatarFile = person.avatarCropPath.takeIf { it.isNotBlank() }?.let { File(it) }
                                        if (avatarFile != null && avatarFile.exists()) {
                                            AsyncImage(
                                                model = avatarFile,
                                                contentDescription = cleanName,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .clip(CircleShape)
                                                    .alpha(if (isTagged) 1f else 0.72f)
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Outlined.Face,
                                                contentDescription = cleanName,
                                                tint = Color(0xFF8E8E93),
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }

                                    // Bottom-right checkmark or + badge
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .size(18.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (isTagged) Color(0xFF0095F6) else Color(0xFF26262B).copy(alpha = 0.85f)
                                            )
                                            .border(1.5.dp, Color(0xFF121214), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (isTagged) Icons.Default.Check else Icons.Default.Add,
                                            contentDescription = if (isTagged) "Tagged" else "Tag",
                                            tint = if (isTagged) Color.White else Color.White.copy(alpha = 0.65f),
                                            modifier = Modifier.size(11.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(5.dp))

                                Text(
                                    text = "@$cleanName",
                                    fontSize = 11.5.sp,
                                    fontWeight = if (isTagged) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (isTagged) Color.White else Color(0xFF8E8E93),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }

            // 3. Minimal Apple/Instagram Camera Info Group (padding(horizontal = 16.dp, vertical = 8.dp))
            val info = exifInfo
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF1C1C20))
                    .padding(16.dp)
            ) {
                // Top Row: Camera/Device title on left, capture date on right
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = info?.deviceModel ?: "Local Gallery Photo",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = info?.capturedDateText ?: formattedDate,
                        fontSize = 12.5.sp,
                        color = Color(0xFF8E8E93)
                    )
                }

                // Middle Row (if lens/exposure specs exist): compact dark pills
                val specs = info?.exposureSpecs.orEmpty()
                if (specs.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        specs.forEach { spec ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF26262B))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = spec,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFFD4D4D8)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Bottom Hairline + File Footer
                HorizontalDivider(
                    color = Color(0xFF2A2A2E),
                    thickness = 0.5.dp
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = info?.footerLine
                        ?: "${media.width} × ${media.height}  •  ${media.displayName}",
                    fontSize = 12.sp,
                    color = Color(0xFF8E8E93),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun NativeActionStripItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = Color.White,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White
        )
    }
}
