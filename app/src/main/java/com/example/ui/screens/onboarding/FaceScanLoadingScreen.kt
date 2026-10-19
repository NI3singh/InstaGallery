// InstaGallery v1.0
package com.example.ui.screens.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.R
import com.example.ui.theme.GrandHotelFont
import com.example.ui.theme.IgBlack
import com.example.ui.theme.IgBlue
import com.example.ui.theme.IgBorderNav
import com.example.ui.theme.IgStoryGradient
import com.example.ui.theme.IgSurfaceModal
import com.example.ui.theme.IgSurfacePill
import com.example.ui.theme.IgTextPrimary
import com.example.ui.theme.IgTextSecondary
import kotlinx.coroutines.delay
import java.io.File

@Composable
fun FaceScanLoadingScreen(
    viewModel: OnboardingViewModel,
    onScanCompleted: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.startInitialScanIfNeeded()
    }

    // Automatically transition to ProfileSetupScreen when scan completes
    LaunchedEffect(uiState.isScanFinished) {
        if (uiState.isScanFinished) {
            delay(650)
            onScanCompleted()
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "scanner_transition")
    val radarRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "radar_rotation"
    )
    val pulseScale1 by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.38f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_scale_1"
    )
    val pulseAlpha1 by infiniteTransition.animateFloat(
        initialValue = 0.55f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_alpha_1"
    )

    val animatedProgress by animateFloatAsState(
        targetValue = if (uiState.isScanFinished) 1f else uiState.progressFraction,
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "scan_progress"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(IgBlack)
            .padding(horizontal = 24.dp, vertical = 20.dp)
            .testTag("face_scan_loading_screen"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Top Brand Title
            Text(
                text = "InstaGallery",
                fontSize = 34.sp,
                fontFamily = GrandHotelFont,
                color = IgTextPrimary
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Animated AI Radar Scanner Ring + Center App Logo / Latest Detected Face
            Box(
                modifier = Modifier.size(168.dp),
                contentAlignment = Alignment.Center
            ) {
                // Expanding Pulse Ring
                Box(
                    modifier = Modifier
                        .size(136.dp)
                        .scale(pulseScale1)
                        .clip(CircleShape)
                        .border(
                            width = 2.dp,
                            color = Color(0xFFDD2A7B).copy(alpha = pulseAlpha1),
                            shape = CircleShape
                        )
                )

                // Rotating Gradient Sweep Arc
                Canvas(
                    modifier = Modifier
                        .size(144.dp)
                        .rotate(radarRotation)
                ) {
                    drawArc(
                        brush = Brush.sweepGradient(
                            listOf(
                                Color.Transparent,
                                Color(0xFFF58529).copy(alpha = 0.3f),
                                Color(0xFFDD2A7B),
                                Color(0xFF8134AF)
                            )
                        ),
                        startAngle = 0f,
                        sweepAngle = 270f,
                        useCenter = false,
                        style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                // Center Squircle Logo
                Box(
                    modifier = Modifier
                        .size(92.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .border(1.5.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(22.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_launcher_background),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize()
                    )
                    Image(
                        painter = painterResource(id = R.drawable.ic_launcher_foreground),
                        contentDescription = "Scanning Gallery",
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Badge at bottom-right of scanner
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(14.dp)
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(if (uiState.isScanFinished) Color(0xFF1DB954) else IgBlue)
                        .border(2.dp, IgBlack, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (uiState.isScanFinished) Icons.Default.Check else Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = if (uiState.isScanFinished) {
                    "AI Face Scan Complete!"
                } else {
                    "Detecting Faces in Your Gallery..."
                },
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = IgTextPrimary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "ML Kit is analyzing your photos & reels on-device to group unique faces into personal Instagram profiles.",
                fontSize = 13.sp,
                color = IgTextSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 19.sp,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Live Counting Metrics Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(IgSurfaceModal)
                    .border(1.dp, IgBorderNav, RoundedCornerShape(18.dp))
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "MEDIA ANALYZED",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = IgTextSecondary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (uiState.totalMediaCount > 0) {
                                "${uiState.processedCount} / ${uiState.totalMediaCount}"
                            } else {
                                "Indexing gallery..."
                            },
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = IgTextPrimary
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(IgBlack)
                            .border(1.dp, IgBorderNav, RoundedCornerShape(50))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "${if (uiState.isScanFinished) 100 else uiState.progressPercent}%",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = IgBlue
                        )
                    }
                }

                // Gradient Progress Bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(IgBlack)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(animatedProgress.coerceIn(0.04f, 1f))
                            .height(8.dp)
                            .clip(RoundedCornerShape(50))
                            .background(IgStoryGradient)
                    )
                }

                // Live Discovered Faces Counter Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Face,
                            contentDescription = null,
                            tint = Color(0xFFDD2A7B),
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Unique People Detected:",
                            fontSize = 13.sp,
                            color = IgTextSecondary
                        )
                    }
                    Text(
                        text = "${uiState.detectedPeople.size} profiles",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = IgTextPrimary
                    )
                }

                // Live Discovered Face Bubbles Strip
                AnimatedVisibility(
                    visible = uiState.detectedPeople.isNotEmpty(),
                    enter = fadeIn() + scaleIn(
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                    )
                ) {
                    LazyRow(
                        contentPadding = PaddingValues(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(
                            items = uiState.detectedPeople,
                            key = { it.person.personId }
                        ) { card ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.width(54.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(IgStoryGradient),
                                    contentAlignment = Alignment.Center
                                ) {
                                    val path = card.person.avatarCropPath
                                    if (path.isNotBlank() && File(path).exists()) {
                                        AsyncImage(
                                            model = File(path),
                                            contentDescription = card.person.name,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(44.dp)
                                                .clip(CircleShape)
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Outlined.Face,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = "${card.person.faceCount}",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = IgTextSecondary
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Privacy Note
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(IgSurfacePill)
                    .padding(horizontal = 14.dp, vertical = 7.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Lock,
                    contentDescription = null,
                    tint = IgBlue,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "100% On-Device ML Kit • Zero cloud uploads",
                    fontSize = 11.5.sp,
                    color = IgTextSecondary
                )
            }

            // Optional button to proceed to setup early if user has a huge library and some faces are already found
            if (uiState.processedCount >= 20 || uiState.isScanFinished) {
                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = {
                        viewModel.finishEarlyAndGoToSetup()
                        onScanCompleted()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = IgBlue),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .testTag("proceed_to_setup_button")
                ) {
                    Text(
                        text = if (uiState.isScanFinished) {
                            "Continue to Name Profiles (${uiState.detectedPeople.size})"
                        } else {
                            "Setup ${uiState.detectedPeople.size} Detected Profiles Now"
                        },
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
            }
        }
    }
}
