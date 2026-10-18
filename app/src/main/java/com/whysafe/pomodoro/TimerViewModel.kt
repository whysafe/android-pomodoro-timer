package com.whysafe.pomodoro

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDate

enum class Phase(val title: String) { FOCUS("Фокус"), SHORT_BREAK("Короткий перерыв"), LONG_BREAK("Длинный перерыв") }

data class Settings(val focusMin: Int = 25, val shortMin: Int = 5, val longMin: Int = 15, val roundsBeforeLong: Int = 4)

data class TimerState(
    val phase: Phase = Phase.FOCUS,
    val totalMs: Long = 25 * 60_000L,
    val remainingMs: Long = 25 * 60_000L,
    val running: Boolean = false,
    val round: Int = 1,
    val doneToday: Int = 0,
    val settings: Settings = Settings(),
) {
    val progress: Float get() = if (totalMs == 0L) 0f else 1f - remainingMs.toFloat() / totalMs
}

class TimerViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("pomodoro", 0)
    /** День, к которому относится счётчик doneToday; объявлен до _state, потому что restore() его меняет. */
    private var countDay: String = LocalDate.now().toString()
    private val _state = MutableStateFlow(restore())
    val state: StateFlow<TimerState> = _state.asStateFlow()
    private var ticker: Job? = null

    fun toggle() = if (_state.value.running) pause() else start()

    private fun start() {
        _state.update { it.copy(running = true) }
        // Считаем от момента запуска по монотонным часам, чтобы таймер не «плыл» из-за задержек delay()
        val endAt = SystemClock.elapsedRealtime() + _state.value.remainingMs
        ticker = viewModelScope.launch {
            while (isActive) {
                val left = endAt - SystemClock.elapsedRealtime()
                if (left <= 0) {
                    finishPhase()
                    break
                }
                _state.update { it.copy(remainingMs = left) }
                delay(200)
            }
        }
    }

    private fun pause() {
        ticker?.cancel()
        _state.update { it.copy(running = false) }
    }

    fun reset() {
        ticker?.cancel()
        _state.update { it.copy(running = false, remainingMs = it.totalMs) }
    }

    fun skip() {
        ticker?.cancel()
        moveToNext(countFocus = false)
    }

    private fun finishPhase() = moveToNext(countFocus = true)

    private fun moveToNext(countFocus: Boolean) {
        // Таймер мог работать через полночь: проверяем дату при каждом обновлении счёта
        val today = LocalDate.now().toString()
        val newDay = today != countDay
        countDay = today
        _state.update { s ->
            val finishedFocus = s.phase == Phase.FOCUS
            val base = if (newDay) 0 else s.doneToday
            val done = if (finishedFocus && countFocus) base + 1 else base
            val next = when {
                !finishedFocus -> Phase.FOCUS
                s.round % s.settings.roundsBeforeLong == 0 -> Phase.LONG_BREAK
                else -> Phase.SHORT_BREAK
            }
            val round = if (!finishedFocus) s.round + 1 else s.round
            val ms = durationOf(next, s.settings)
            s.copy(phase = next, totalMs = ms, remainingMs = ms, running = false, round = round, doneToday = done)
        }
        save()
    }

    fun changeSetting(transform: (Settings) -> Settings) {
        _state.update { s ->
            val newSettings = transform(s.settings).let {
                it.copy(focusMin = it.focusMin.coerceIn(5, 90), shortMin = it.shortMin.coerceIn(1, 30), longMin = it.longMin.coerceIn(5, 60))
            }
            val ms = durationOf(s.phase, newSettings)
            // Пока таймер не запущен, сразу применяем новую длительность текущей фазы.
            // На паузе пройденное время сохраняется: оставшееся = новая длительность минус прошедшее
            val elapsed = s.totalMs - s.remainingMs
            if (s.running) s.copy(settings = newSettings)
            else s.copy(settings = newSettings, totalMs = ms, remainingMs = (ms - elapsed).coerceAtLeast(0L))
        }
        save()
    }

    private fun durationOf(phase: Phase, s: Settings): Long = 60_000L * when (phase) {
        Phase.FOCUS -> s.focusMin
        Phase.SHORT_BREAK -> s.shortMin
        Phase.LONG_BREAK -> s.longMin
    }

    private fun restore(): TimerState {
        val settings = Settings(prefs.getInt("focus", 25), prefs.getInt("short", 5), prefs.getInt("long", 15))
        val today = LocalDate.now().toString()
        val done = if (prefs.getString("day", "") == today) prefs.getInt("done", 0) else 0
        countDay = today
        val ms = settings.focusMin * 60_000L
        return TimerState(totalMs = ms, remainingMs = ms, doneToday = done, settings = settings)
    }

    private fun save() {
        val s = _state.value
        prefs.edit()
            .putInt("focus", s.settings.focusMin).putInt("short", s.settings.shortMin).putInt("long", s.settings.longMin)
            .putString("day", countDay).putInt("done", s.doneToday)
            .apply()
    }
}
