package com.peykr.app.ui.splash

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.peykr.app.R
import com.peykr.app.ui.theme.LightBackground
import com.peykr.app.ui.theme.LightPrimaryAccent
import com.peykr.app.ui.theme.LightTextSecondary
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
  onLoaded: () -> Unit,
  modifier: Modifier = Modifier
) {
  LaunchedEffect(Unit) {
    // Elegant splash display duration to let system components warm up
    delay(3500L)
    onLoaded()
  }

  val infiniteTransition = rememberInfiniteTransition(label = "splash")
  val alphaAnim by infiniteTransition.animateFloat(
    initialValue = 0.6f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
      animation = tween(800, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "splashAlpha"
  )

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(LightBackground),
    contentAlignment = Alignment.Center
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center
    ) {
      // The original Peykr logo is rendered ONLY here on the Splash Screen
      Image(
        painter = painterResource(R.drawable.peykr_logo_light),
        contentDescription = "Peykr",
        contentScale = ContentScale.Fit,
        modifier = Modifier.size(180.dp)
      )

      Spacer(Modifier.height(36.dp))

      CircularProgressIndicator(
        modifier = Modifier.size(28.dp),
        color = LightPrimaryAccent,
        strokeWidth = 2.5.dp
      )

      Spacer(Modifier.height(16.dp))

      Text(
        text = "Uygulama hazırlanıyor...",
        color = LightTextSecondary,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        modifier = Modifier.alpha(alphaAnim)
      )
    }
  }
}
