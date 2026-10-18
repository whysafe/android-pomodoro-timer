package com.whysafe.pomodoro

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF2B2D42))) { TimerScreen() }
        }
    }
}

private fun phaseColor(p: Phase) = when (p) {
    Phase.FOCUS -> Color(0xFFE5483B)
    Phase.SHORT_BREAK -> Color(0xFF2E9E6A)
    Phase.LONG_BREAK -> Color(0xFF3B6FD9)
}

@Composable
fun TimerScreen(vm: TimerViewModel = viewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val bg by animateColorAsState(phaseColor(s.phase), label = "bg")

    // Экран не гаснет, пока идёт таймер
    val view = LocalView.current
    DisposableEffect(s.running) {
        view.keepScreenOn = s.running
        onDispose { view.keepScreenOn = false }
    }

    val minutes = (s.remainingMs + 999) / 1000 / 60
    val seconds = (s.remainingMs + 999) / 1000 % 60
    val timeText = "%02d:%02d".format(minutes, seconds)

    Column(
        Modifier.fillMaxSize().background(bg).safeDrawingPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(s.phase.title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Text(
            if (s.phase == Phase.FOCUS) "Раунд ${(s.round - 1) % s.settings.roundsBeforeLong + 1} из ${s.settings.roundsBeforeLong}" else "Отдохните и подвигайтесь",
            color = Color.White.copy(alpha = .8f),
        )
        Spacer(Modifier.height(28.dp))

        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(280.dp).semantics { contentDescription = "Осталось $timeText" }) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 18.dp.toPx()
                val arcSize = Size(size.width - stroke, size.height - stroke)
                val topLeft = Offset(stroke / 2, stroke / 2)
                drawArc(Color.White.copy(alpha = .25f), 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
                drawArc(Color.White, -90f, 360f * s.progress, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            }
            Text(timeText, color = Color.White, fontSize = 64.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(32.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = vm::reset) { Text("Сбросить", color = Color.White) }
            Button(
                onClick = vm::toggle,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = bg),
                shape = RoundedCornerShape(50),
                modifier = Modifier.width(150.dp).height(56.dp),
            ) { Text(if (s.running) "Пауза" else "Старт", fontSize = 20.sp, fontWeight = FontWeight.Bold) }
            TextButton(onClick = vm::skip) { Text("Пропустить", color = Color.White) }
        }

        Spacer(Modifier.height(24.dp))
        Text("Сегодня: ${s.doneToday} ${plural(s.doneToday)}", color = Color.White, fontSize = 18.sp)
        Text("🍅".repeat(s.doneToday.coerceAtMost(12)), fontSize = 22.sp)

        Spacer(Modifier.weight(1f))
        Column(
            Modifier.fillMaxWidth().background(Color.White.copy(alpha = .15f), RoundedCornerShape(20.dp)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Stepper("Фокус", s.settings.focusMin, enabled = !s.running) { d -> vm.changeSetting { it.copy(focusMin = it.focusMin + d * 5) } }
            Stepper("Короткий перерыв", s.settings.shortMin, enabled = !s.running) { d -> vm.changeSetting { it.copy(shortMin = it.shortMin + d) } }
            Stepper("Длинный перерыв", s.settings.longMin, enabled = !s.running) { d -> vm.changeSetting { it.copy(longMin = it.longMin + d * 5) } }
        }
    }
}

@Composable
private fun Stepper(label: String, value: Int, enabled: Boolean, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White, modifier = Modifier.weight(1f))
        OutlinedButton(onClick = { onChange(-1) }, enabled = enabled, modifier = Modifier.size(40.dp), contentPadding = ButtonDefaults.TextButtonContentPadding) {
            Text("−", color = Color.White, fontSize = 18.sp)
        }
        Text("$value мин", color = Color.White, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(72.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        OutlinedButton(onClick = { onChange(1) }, enabled = enabled, modifier = Modifier.size(40.dp), contentPadding = ButtonDefaults.TextButtonContentPadding) {
            Text("+", color = Color.White, fontSize = 18.sp)
        }
    }
}

private fun plural(n: Int): String {
    val m10 = n % 10
    val m100 = n % 100
    return when {
        m10 == 1 && m100 != 11 -> "помидор"
        m10 in 2..4 && m100 !in 12..14 -> "помидора"
        else -> "помидоров"
    }
}
