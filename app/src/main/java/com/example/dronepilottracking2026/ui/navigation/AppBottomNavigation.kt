package com.example.dronepilottracking2026.ui.navigation

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dronepilottracking2026.ui.theme.TacticalAmber
import com.example.dronepilottracking2026.ui.theme.TacticalCard
import com.example.dronepilottracking2026.ui.theme.TacticalCyan
import com.example.dronepilottracking2026.ui.theme.TacticalMuted
import com.example.dronepilottracking2026.ui.theme.TacticalRed
import com.example.dronepilottracking2026.ui.theme.TacticalText

@Composable
fun AppBottomNavigation(
    currentDestination: AppDestination,
    onDestinationSelected: (AppDestination) -> Unit,
    sosActive: Boolean = false,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "navbar-sos-blink")
    val sosAlpha by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(650),
            repeatMode = RepeatMode.Reverse
        ),
        label = "navbar-sos-alpha"
    )
    val navigationBackground = if (sosActive) TacticalRed.copy(alpha = sosAlpha) else TacticalCard

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(navigationBackground)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppDestination.entries.forEach { destination ->
            val selected = destination == currentDestination
            val activeColor = if (sosActive) Color.White else TacticalCyan
            val iconColor = when {
                selected -> activeColor
                sosActive -> Color.White.copy(alpha = 0.8f)
                else -> TacticalMuted
            }
            val labelColor = when {
                selected -> if (sosActive) Color.White else TacticalText
                sosActive -> Color.White.copy(alpha = 0.8f)
                else -> TacticalMuted
            }

            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (selected) {
                            if (sosActive) Color.White.copy(alpha = 0.18f)
                            else TacticalCyan.copy(alpha = 0.14f)
                        } else {
                            Color.Transparent
                        }
                    )
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = { onDestinationSelected(destination) }
                    )
                    .padding(horizontal = 17.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                NavigationIconView(destination.icon, iconColor)
                Text(
                    text = destination.label,
                    color = labelColor,
                    fontSize = 10.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}

@Composable
private fun NavigationIconView(icon: NavigationIcon, color: Color) {
    Canvas(modifier = Modifier.size(24.dp)) {
        val stroke = Stroke(
            width = 1.8.dp.toPx(),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
        when (icon) {
            NavigationIcon.HOME -> {
                val roof = Path().apply {
                    moveTo(size.width * 0.16f, size.height * 0.48f)
                    lineTo(size.width * 0.50f, size.height * 0.16f)
                    lineTo(size.width * 0.84f, size.height * 0.48f)
                }
                drawPath(roof, color, style = stroke)
                drawRoundRect(
                    color = color,
                    topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.25f, size.height * 0.43f),
                    size = androidx.compose.ui.geometry.Size(size.width * 0.50f, size.height * 0.40f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()),
                    style = stroke
                )
                drawLine(
                    color = color,
                    start = androidx.compose.ui.geometry.Offset(size.width * 0.50f, size.height * 0.83f),
                    end = androidx.compose.ui.geometry.Offset(size.width * 0.50f, size.height * 0.61f),
                    strokeWidth = stroke.width,
                    cap = StrokeCap.Round
                )
            }

            NavigationIcon.SPECTRUM -> {
                val wave = Path().apply {
                    moveTo(size.width * 0.08f, size.height * 0.58f)
                    lineTo(size.width * 0.24f, size.height * 0.58f)
                    lineTo(size.width * 0.35f, size.height * 0.30f)
                    lineTo(size.width * 0.48f, size.height * 0.76f)
                    lineTo(size.width * 0.62f, size.height * 0.20f)
                    lineTo(size.width * 0.76f, size.height * 0.58f)
                    lineTo(size.width * 0.92f, size.height * 0.58f)
                }
                drawPath(wave, color, style = stroke)
                drawLine(
                    color = color.copy(alpha = 0.45f),
                    start = androidx.compose.ui.geometry.Offset(size.width * 0.08f, size.height * 0.88f),
                    end = androidx.compose.ui.geometry.Offset(size.width * 0.92f, size.height * 0.88f),
                    strokeWidth = 1.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            NavigationIcon.SETTINGS -> {
                val center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
                val outerRadius = size.minDimension * 0.39f
                val innerRadius = size.minDimension * 0.29f
                val gearPath = Path()
                val toothCount = 8
                repeat(toothCount * 4) { index ->
                    val angle = Math.toRadians(index * (360.0 / (toothCount * 4)))
                    val radius = when (index % 4) {
                        0, 1 -> outerRadius
                        else -> innerRadius
                    }
                    val point = androidx.compose.ui.geometry.Offset(
                        center.x + kotlin.math.cos(angle).toFloat() * radius,
                        center.y + kotlin.math.sin(angle).toFloat() * radius
                    )
                    if (index == 0) gearPath.moveTo(point.x, point.y)
                    else gearPath.lineTo(point.x, point.y)
                }
                gearPath.close()
                drawPath(gearPath, color, style = stroke)
                drawCircle(
                    color = color,
                    radius = size.minDimension * 0.12f,
                    center = center,
                    style = Stroke(width = 1.6.dp.toPx())
                )
            }
        }
    }
}

fun Modifier.clickableWithoutRipple(onClick: () -> Unit): Modifier = clickable(
    indication = null,
    interactionSource = MutableInteractionSource(),
    onClick = onClick
)
