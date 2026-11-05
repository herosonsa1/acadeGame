package com.jjalddalmark.minigamerullet.data

import android.content.Context
import com.jjalddalmark.minigamerullet.model.BuiltInRouletteTemplates
import com.jjalddalmark.minigamerullet.model.RouletteHistoryRecord
import com.jjalddalmark.minigamerullet.model.RouletteStorageCodec
import com.jjalddalmark.minigamerullet.model.RouletteWheelPreset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

data class RouletteVaultSnapshot(
    val activePreset: RouletteWheelPreset,
    val savedPresets: List<RouletteWheelPreset>,
    val historyRecords: List<RouletteHistoryRecord>
)

/**
 * 사용자가 만든 룰렛 보관함 · 현재 진행 중인 룰렛 · 추첨 기록을 IO 스레드에서 영구 보관하는 저장소
 * - storageNamespace = "decision": 만능 결정 룰렛+ 전용 기존 파일명 100% 보존
 * - storageNamespace = "windmill": 언덕 위 풍차 룰렛 전용 독립 보관함 및 기록 파일 사용
 */
class RouletteStorageRepository(
    context: Context,
    private val storageNamespace: String = "decision",
    private val defaultTemplatesProvider: () -> List<RouletteWheelPreset> = {
        BuiltInRouletteTemplates.defaultTemplates()
    }
) {
    private val appContext = context.applicationContext
    private val mutex = Mutex()

    private val filePrefix: String
        get() = if (storageNamespace == "decision") "roulette" else storageNamespace

    private val savedWheelsFile: File
        get() = File(appContext.filesDir, "${filePrefix}_saved_wheels_v1.dat")

    private val activeWheelFile: File
        get() = File(appContext.filesDir, "${filePrefix}_active_wheel_v1.dat")

    private val historyFile: File
        get() = File(appContext.filesDir, "${filePrefix}_history_v1.dat")

    suspend fun loadInitialSnapshot(): RouletteVaultSnapshot = withContext(Dispatchers.IO) {
        mutex.withLock {
            val saved = if (savedWheelsFile.exists()) {
                val parsed = runCatching {
                    RouletteStorageCodec.decodePresetList(savedWheelsFile.readText(Charsets.UTF_8))
                }.getOrDefault(emptyList())
                parsed.ifEmpty { seedDefaultTemplatesLocked() }
            } else {
                seedDefaultTemplatesLocked()
            }

            val active = if (activeWheelFile.exists()) {
                runCatching {
                    RouletteStorageCodec.decodePreset(activeWheelFile.readText(Charsets.UTF_8))
                }.getOrNull() ?: saved.first()
            } else {
                saved.first().also { writeActiveLocked(it) }
            }

            val history = if (historyFile.exists()) {
                runCatching {
                    RouletteStorageCodec.decodeHistoryList(historyFile.readText(Charsets.UTF_8))
                }.getOrDefault(emptyList())
            } else {
                emptyList()
            }

            RouletteVaultSnapshot(
                activePreset = active,
                savedPresets = saved,
                historyRecords = history
            )
        }
    }

    suspend fun saveActivePreset(preset: RouletteWheelPreset) = withContext(Dispatchers.IO) {
        mutex.withLock {
            writeActiveLocked(preset)
        }
    }

    /**
     * 현재 룰렛을 보관함에 덮어쓰기(동일 ID 존재 시) 또는 새 항목으로 저장
     */
    suspend fun upsertSavedPreset(
        preset: RouletteWheelPreset,
        saveAsNewCopy: Boolean = false
    ): List<RouletteWheelPreset> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val currentList = readSavedLocked().toMutableList()
            val now = System.currentTimeMillis()
            val target = if (saveAsNewCopy) {
                preset.copy(
                    id = UUID.randomUUID().toString(),
                    updatedAtMillis = now
                )
            } else {
                preset.copy(updatedAtMillis = now)
            }

            val existingIdx = currentList.indexOfFirst { it.id == target.id }
            if (existingIdx >= 0 && !saveAsNewCopy) {
                currentList[existingIdx] = target
            } else {
                currentList.add(0, target)
            }
            writeSavedLocked(currentList)
            writeActiveLocked(target)
            currentList
        }
    }

    suspend fun duplicateSavedPreset(preset: RouletteWheelPreset): List<RouletteWheelPreset> =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val currentList = readSavedLocked().toMutableList()
                val copyPreset = preset.copy(
                    id = UUID.randomUUID().toString(),
                    title = "${preset.title} (사본)".take(32),
                    playCount = 0,
                    updatedAtMillis = System.currentTimeMillis()
                )
                currentList.add(0, copyPreset)
                writeSavedLocked(currentList)
                currentList
            }
        }

    suspend fun deleteSavedPreset(presetId: String): List<RouletteWheelPreset> =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val remaining = readSavedLocked().filterNot { it.id == presetId }
                val safeList = remaining.ifEmpty { defaultTemplatesProvider() }
                writeSavedLocked(safeList)
                safeList
            }
        }

    suspend fun restoreBuiltInTemplates(): List<RouletteWheelPreset> =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val current = readSavedLocked().toMutableList()
                val existingIds = current.map { it.id }.toSet()
                val missing = defaultTemplatesProvider().filter { it.id !in existingIds }
                val merged = current + missing
                writeSavedLocked(merged)
                merged
            }
        }

    suspend fun syncPlayerPresetsInVault(playerNames: List<String>): RouletteVaultSnapshot =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val currentSaved = readSavedLocked().map { preset ->
                    BuiltInRouletteTemplates.syncPlayerDrivenPreset(preset, playerNames)
                }
                writeSavedLocked(currentSaved)

                val rawActive = if (activeWheelFile.exists()) {
                    runCatching {
                        RouletteStorageCodec.decodePreset(activeWheelFile.readText(Charsets.UTF_8))
                    }.getOrNull() ?: currentSaved.first()
                } else {
                    currentSaved.first()
                }
                val syncedActive = BuiltInRouletteTemplates.syncPlayerDrivenPreset(rawActive, playerNames)
                writeActiveLocked(syncedActive)

                val history = if (historyFile.exists()) {
                    runCatching {
                        RouletteStorageCodec.decodeHistoryList(historyFile.readText(Charsets.UTF_8))
                    }.getOrDefault(emptyList())
                } else {
                    emptyList()
                }

                RouletteVaultSnapshot(
                    activePreset = syncedActive,
                    savedPresets = currentSaved,
                    historyRecords = history
                )
            }
        }

    suspend fun appendHistoryRecord(
        record: RouletteHistoryRecord,
        updatedActivePreset: RouletteWheelPreset
    ): List<RouletteHistoryRecord> = withContext(Dispatchers.IO) {
        mutex.withLock {
            writeActiveLocked(updatedActivePreset)
            // 보관함에도 동일 ID 룰렛이 있으면 플레이 횟수 동기화 (제외 상태는 활성 스냅샷 기준)
            val saved = readSavedLocked().toMutableList()
            val idx = saved.indexOfFirst { it.id == updatedActivePreset.id }
            if (idx >= 0) {
                saved[idx] = saved[idx].copy(playCount = updatedActivePreset.playCount)
                writeSavedLocked(saved)
            }

            val history = if (historyFile.exists()) {
                runCatching {
                    RouletteStorageCodec.decodeHistoryList(historyFile.readText(Charsets.UTF_8))
                }.getOrDefault(emptyList())
            } else {
                emptyList()
            }
            val updatedHistory = (listOf(record) + history).take(120)
            historyFile.writeText(RouletteStorageCodec.encodeHistoryList(updatedHistory), Charsets.UTF_8)
            updatedHistory
        }
    }

    suspend fun clearHistory(): List<RouletteHistoryRecord> = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (historyFile.exists()) {
                historyFile.delete()
            }
            emptyList()
        }
    }

    private fun seedDefaultTemplatesLocked(): List<RouletteWheelPreset> {
        val defaults = defaultTemplatesProvider()
        writeSavedLocked(defaults)
        return defaults
    }

    private fun readSavedLocked(): List<RouletteWheelPreset> {
        if (!savedWheelsFile.exists()) return seedDefaultTemplatesLocked()
        val parsed = runCatching {
            RouletteStorageCodec.decodePresetList(savedWheelsFile.readText(Charsets.UTF_8))
        }.getOrDefault(emptyList())
        return parsed.ifEmpty { seedDefaultTemplatesLocked() }
    }

    private fun writeSavedLocked(list: List<RouletteWheelPreset>) {
        savedWheelsFile.writeText(RouletteStorageCodec.encodePresetList(list), Charsets.UTF_8)
    }

    private fun writeActiveLocked(preset: RouletteWheelPreset) {
        activeWheelFile.writeText(RouletteStorageCodec.encodePreset(preset), Charsets.UTF_8)
    }
}
