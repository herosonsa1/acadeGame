package com.example.fairwaypicnic.data

import android.content.Context
import com.example.fairwaypicnic.model.AppSettings
import com.example.fairwaypicnic.model.KoreanGolfNameHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 출전 선수 이름·프리셋·게임 진행 시간·개별 사운드 채널 볼륨을 IO 스레드에서 영속 저장하는 저장소
 */
class PlayerPreferencesRepository(context: Context) {
    private val appContext = context.applicationContext
    private val prefsName = "fairway_picnic_prefs"

    suspend fun loadSettings(): AppSettings = withContext(Dispatchers.IO) {
        val prefs = appContext.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

        // v1.1 기본값: 1번캐디백 ~ 4번캐디백
        val rawNamesV2 = prefs.getString("player_names_v2", null)
        val activeNames = parseNamesOrNull(rawNamesV2)
            ?: KoreanGolfNameHelper.defaultCaddieBagNames(4)

        // 맞춤 설정 이름 슬롯: 기존 설정된 이름이 있으면 이어받고 없으면 기본 샘플 유지
        val rawCustomV2 = prefs.getString("saved_custom_names_v2", null)
            ?: prefs.getString("player_names", null)
        val customNames = parseNamesOrNull(rawCustomV2)
            ?: KoreanGolfNameHelper.defaultSampleCustomNames(activeNames.size)

        val duration = prefs.getInt("game_duration_sec", 10).let {
            when (it) {
                5, 10, 15 -> it
                else -> 10
            }
        }

        AppSettings(
            playerNames = activeNames,
            savedCustomNames = customNames,
            gameDurationSeconds = duration,
            soundEnabled = prefs.getBoolean("sound_enabled", true),
            bgmEnabled = prefs.getBoolean("bgm_enabled", true),
            bgmVolume = prefs.getFloat("bgm_volume", 0.65f).coerceIn(0f, 1f),
            sfxEnabled = prefs.getBoolean("sfx_enabled", true),
            sfxVolume = prefs.getFloat("sfx_volume", 0.88f).coerceIn(0f, 1f),
            heartbeatEnabled = prefs.getBoolean("heartbeat_enabled", true),
            heartbeatVolume = prefs.getFloat("heartbeat_volume", 0.92f).coerceIn(0f, 1f),
            resultFanfareEnabled = prefs.getBoolean("result_fanfare_enabled", true),
            resultFanfareVolume = prefs.getFloat("result_fanfare_volume", 0.94f).coerceIn(0f, 1f),
            muteInVibrateMode = prefs.getBoolean("mute_in_vibrate_mode", false),
            hapticEnabled = prefs.getBoolean("haptic_enabled", true),
            slowMotionEnabled = prefs.getBoolean("slow_motion_enabled", true)
        )
    }

    suspend fun saveSettings(settings: AppSettings) = withContext(Dispatchers.IO) {
        val prefs = appContext.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        prefs.edit()
            .putString("player_names_v2", settings.playerNames.joinToString("|"))
            .putString("saved_custom_names_v2", settings.savedCustomNames.joinToString("|"))
            .putInt("game_duration_sec", settings.normalizedDurationSeconds)
            .putBoolean("sound_enabled", settings.soundEnabled)
            .putBoolean("bgm_enabled", settings.bgmEnabled)
            .putFloat("bgm_volume", settings.bgmVolume.coerceIn(0f, 1f))
            .putBoolean("sfx_enabled", settings.sfxEnabled)
            .putFloat("sfx_volume", settings.sfxVolume.coerceIn(0f, 1f))
            .putBoolean("heartbeat_enabled", settings.heartbeatEnabled)
            .putFloat("heartbeat_volume", settings.heartbeatVolume.coerceIn(0f, 1f))
            .putBoolean("result_fanfare_enabled", settings.resultFanfareEnabled)
            .putFloat("result_fanfare_volume", settings.resultFanfareVolume.coerceIn(0f, 1f))
            .putBoolean("mute_in_vibrate_mode", settings.muteInVibrateMode)
            .putBoolean("haptic_enabled", settings.hapticEnabled)
            .putBoolean("slow_motion_enabled", settings.slowMotionEnabled)
            .apply()
    }

    private fun parseNamesOrNull(raw: String?): List<String>? {
        if (raw.isNullOrBlank()) return null
        val list = raw.split("|").map { it.trim() }.filter { it.isNotEmpty() }
        return if (list.size >= 2) list.take(6) else null
    }
}
