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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.OutlinedExtendedIcons
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.local.entity.MediaEntity
import com.example.data.local.entity.PersonEntity
import com.example.ui.theme.IgBlack
import com.example.ui.theme.IgBlue
import com.example.ui.theme.IgBorderNav
import com.example.ui.theme.IgSurfacePill
import com.example.ui.theme.IgTextPrimary
import com.example.ui.theme.IgTextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    // Check UUID-like or hex-like strings
    val hexAndHyphenCount = stem.count { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' || it == '-' || it == '_' }
    if (stem.length >= 12 && hexAndHyphenCount == stem.length && digitCount >= 3) return true
    return false
}

private val QuickMemoryTags = listOf(
    "Family ❤️",
    "Throwback ✨",
    "Trip 🌍",
    "Celebration 🎉",
    "Golden Hour 🌅"
)

/**
 * ModalBottomSheet for adding or editing a persistent custom memory caption (MediaEntity.customCaption).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
        containerColor = Color(0xFF121216)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 10.dp)
                .testTag("caption_editor_sheet")
        ) {
            Text(
                text = "Memory Note & Caption",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Saved privately to your device gallery database",
                fontSize = 12.sp,
                color = Color(0xFF8E8E93)
            )

            Spacer(modifier = Modifier.height(14.dp))

            // 48.dp rounded thumbnail preview + @AuthorName and capture date
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF18181E))
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = media.uri,
                    contentDescription = media.displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF101014))
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "@${primaryAuthorName.removePrefix("@")}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = formattedDate,
                        fontSize = 12.sp,
                        color = Color(0xFF8E8E93)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Multi-line OutlinedTextField (#1C1C22 fill, #0095F6 focused border, RoundedCornerShape(14.dp))
            OutlinedTextField(
                value = captionInput,
                onValueChange = { captionInput = it },
                placeholder = {
                    Text(
                        text = "Write a caption or memory for this photo...",
                        color = Color(0xFF737373),
                        fontSize = 14.sp
                    )
                },
                minLines = 3,
                maxLines = 5,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFF1C1C22),
                    unfocusedContainerColor = Color(0xFF1C1C22),
                    focusedBorderColor = Color(0xFF0095F6),
                    unfocusedBorderColor = Color(0xFF2C2C34),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = Color(0xFF0095F6)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("caption_text_field")
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Quick memory tags:",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF8E8E93)
            )

            Spacer(modifier = Modifier.height(8.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QuickMemoryTags.forEach { chipText ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color(0xFF1E1E24))
                            .border(1.dp, Color(0xFF2E2E38), RoundedCornerShape(50))
                            .clickable {
                                val trimmed = captionInput.trim()
                                captionInput = if (trimmed.isEmpty()) {
                                    chipText
                                } else if (trimmed.contains(chipText)) {
                                    trimmed
                                } else {
                                    "$trimmed  •  $chipText"
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = chipText,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFE4E4E7)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (!media.customCaption.isNullOrBlank() || captionInput.isNotBlank()) {
                    OutlinedButton(
                        onClick = {
                            onSaveCaption(null)
                            onDismiss()
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFFFF453A)
                        ),
                        modifier = Modifier
                            .weight(0.4f)
                            .height(48.dp)
                            .testTag("clear_caption_button")
                    ) {
                        Text(
                            text = "Clear",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Button(
                    onClick = {
                        onSaveCaption(captionInput.trim().takeIf { it.isNotEmpty() })
                        onDismiss()
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("save_caption_button")
                ) {
                    Text(
                        text = "Save Note",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

data class OfflineExifMetadata(
    val dimensionsText: String,
    val capturedText: String,
    val cameraMakeModel: String?,
    val lensAndExposure: String?,
    val fileSizeText: String?,
    val fileName: String,
    val mimeType: String
)

private suspend fun loadOfflineExifMetadata(
    context: Context,
    media: MediaEntity,
    formattedDate: String
): OfflineExifMetadata = withContext(Dispatchers.IO) {
    val uri = runCatching { Uri.parse(media.uri) }.getOrNull()

    var exifW = media.width
    var exifH = media.height
    var cameraDevice: String? = null
    var lensDetails: String? = null
    var capturedTime = formattedDate
    var sizeStr: String? = null

    if (uri != null) {
        // Query file size if available
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

        // Read EXIF tags
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

                val exposureParts = mutableListOf<String>()
                val focal35 = exif.getAttributeInt(ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM, 0)
                if (focal35 > 0) {
                    exposureParts.add("${focal35}mm")
                } else {
                    val focalRaw = exif.getAttributeDouble(ExifInterface.TAG_FOCAL_LENGTH, 0.0)
                    if (focalRaw > 0.0) {
                        exposureParts.add(String.format(Locale.US, "%.1fmm", focalRaw))
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

                if (exposureParts.isNotEmpty()) {
                    lensDetails = exposureParts.joinToString("  •  ")
                }
            }
        }
    }

    val mp = if (exifW > 0 && exifH > 0) {
        val megapixels = (exifW.toLong() * exifH.toLong()) / 1_000_000.0
        String.format(Locale.US, "%.1f MP", megapixels)
    } else null

    val dimText = if (exifW > 0 && exifH > 0) {
        if (mp != null) "${exifW} × ${exifH} px ($mp)" else "${exifW} × ${exifH} px"
    } else {
        "Original Resolution"
    }

    val fullDateSdf = SimpleDateFormat("MMMM d, yyyy • h:mm a", Locale.getDefault())
    capturedTime = fullDateSdf.format(Date(media.dateAddedSeconds * 1000L))

    OfflineExifMetadata(
        dimensionsText = dimText,
        capturedText = capturedTime,
        cameraMakeModel = cameraDevice,
        lensAndExposure = lensDetails,
        fileSizeText = sizeStr,
        fileName = media.displayName,
        mimeType = media.mimeType
    )
}

/**
 * Post Inspector & Offline EXIF Sheet triggered by the top-right `⋮` menu on PostCard.
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
    onOpenHeartThemePicker: () -> Unit,
    onShareOriginal: () -> Unit,
    onAuthorClick: (Long) -> Unit,
    onTagPerson: ((Long, Long) -> Unit)?,
    onUntagPerson: ((Long, Long) -> Unit)?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var exifInfo by remember(media.mediaId) { mutableStateOf<OfflineExifMetadata?>(null) }

    val taggedIds = remember(authors) { authors.map { it.personId }.toSet() }
    val untaggedPeople = remember(allPeople, taggedIds) {
        allPeople.filter { !taggedIds.contains(it.personId) }
    }

    LaunchedEffect(media.mediaId) {
        exifInfo = loadOfflineExifMetadata(context, media, formattedDate)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF121216)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .testTag("post_inspector_sheet")
        ) {
            Text(
                text = "Post Inspector & Details",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(14.dp))

            // 1. Quick Action Pills at Top
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                InspectorActionPill(
                    icon = SnapshotStudioIcon,
                    label = "InstaCard\nSnapshot",
                    accentColor = Color(0xFF0095F6),
                    onClick = {
                        onDismiss()
                        onOpenInstaCardStudio()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("inspector_instacard_action")
                )

                InspectorActionPill(
                    icon = Icons.Outlined.Edit,
                    label = "Edit Memory\nNote",
                    accentColor = Color.White,
                    onClick = {
                        onDismiss()
                        onOpenCaptionEditor()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("inspector_caption_action")
                )

                InspectorActionPill(
                    icon = Icons.Outlined.Share,
                    label = "Share\nOriginal",
                    accentColor = Color.White,
                    onClick = {
                        onDismiss()
                        onShareOriginal()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("inspector_share_action")
                )

                InspectorActionPill(
                    icon = OutlinedExtendedIcons.FavoriteBorder,
                    label = "Heart\nColor",
                    accentColor = Color(0xFFFF3040),
                    onClick = {
                        onDismiss()
                        onOpenHeartThemePicker()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("inspector_heart_color_action")
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 2. Offline EXIF & File Details Card (#18181E container, 14.dp radius)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF18181E))
                    .border(1.dp, Color(0xFF26262E), RoundedCornerShape(14.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = Color(0xFF0095F6),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "OFFLINE EXIF & CAMERA METADATA",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF8E8E93),
                        letterSpacing = 0.6.sp
                    )
                }

                val info = exifInfo
                ExifDetailRow(
                    label = "Dimensions",
                    value = info?.dimensionsText ?: "${media.width} × ${media.height} px"
                )
                ExifDetailRow(
                    label = "Captured",
                    value = info?.capturedText ?: formattedDate
                )
                if (!info?.cameraMakeModel.isNullOrBlank()) {
                    ExifDetailRow(
                        label = "Camera / Device",
                        value = info?.cameraMakeModel ?: ""
                    )
                }
                if (!info?.lensAndExposure.isNullOrBlank()) {
                    ExifDetailRow(
                        label = "Lens / Exposure",
                        value = info?.lensAndExposure ?: ""
                    )
                }
                ExifDetailRow(
                    label = "File Name",
                    value = if (info?.fileSizeText != null) {
                        "${media.displayName} (${info.fileSizeText})"
                    } else {
                        media.displayName
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 3. People Tagged & Manual Tagging Section
            Text(
                text = if (authors.isEmpty()) "No people tagged in this photo" else "People in this memory",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = IgTextSecondary
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (authors.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    authors.forEach { person ->
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(IgBlack)
                                .border(1.dp, IgBorderNav, RoundedCornerShape(50))
                                .clickable {
                                    onDismiss()
                                    onAuthorClick(person.personId)
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OverlappingAvatars(
                                authors = listOf(person),
                                avatarSize = 22.dp
                            )
                            Text(
                                text = "@${person.name.removePrefix("@")}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = IgTextPrimary
                            )
                            if (onUntagPerson != null) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remove tag",
                                    tint = IgTextSecondary,
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable {
                                            onUntagPerson(media.mediaId, person.personId)
                                        }
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            if (onTagPerson != null && untaggedPeople.isNotEmpty()) {
                HorizontalDivider(color = IgBorderNav, thickness = 0.5.dp)
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Tag a person:",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = IgTextSecondary
                )
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    untaggedPeople.forEach { person ->
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(IgSurfacePill)
                                .border(1.dp, IgBorderNav, RoundedCornerShape(50))
                                .clickable {
                                    onTagPerson(media.mediaId, person.personId)
                                }
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Tag",
                                tint = IgBlue,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "@${person.name.removePrefix("@")}",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = IgTextPrimary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun InspectorActionPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF18181E))
            .border(1.dp, Color(0xFF282830), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(Color(0xFF22222A)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = accentColor,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            lineHeight = 13.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFFE4E4E7),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
private fun ExifDetailRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            fontSize = 12.5.sp,
            color = Color(0xFF8E8E93),
            modifier = Modifier.width(116.dp)
        )
        Text(
            text = value,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFFF4F4F6),
            modifier = Modifier.weight(1f)
        )
    }
}
