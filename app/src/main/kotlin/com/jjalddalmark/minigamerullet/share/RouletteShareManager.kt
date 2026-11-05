package com.jjalddalmark.minigamerullet.share

import android.app.Activity
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Telephony
import android.widget.Toast
import androidx.core.content.FileProvider
import com.jjalddalmark.minigamerullet.model.RouletteSpecialEffect
import com.jjalddalmark.minigamerullet.model.RouletteSpinResult
import java.io.File
import java.util.Locale

/**
 * 룰렛 결과 이미지(JPEG) 및 스핀 전 과정 영상(MP4) 메신저 공유 채널
 */
enum class RouletteShareChannel(val label: String) {
    KAKAOTALK("카카오톡"),
    SMS_MMS("문자 메시지"),
    TELEGRAM("텔레그램"),
    SYSTEM_MORE("더보기 공유")
}

/**
 * 카카오톡 · 문자(MMS) · 텔레그램 · 시스템 공유시트 안전 발송 매니저
 * - AP-ANDROID-SHARE-CLIPDATA-NULL-RESOLVER-NPE-AND-TRAMPOLINE-URI 가드레일 준수:
 *   1) ClipData.newUri(null, ...) 호출 금지 -> ClipDescription + ClipData.Item 직접 생성
 *   2) 카카오톡 전송 시 EXTRA_TEXT 동시 첨부로 인한 미디어 누락 방어 (스트림 단독 첨부 + 클립보드 보조 복사)
 *   3) 타겟 패키지들에 FLAG_GRANT_READ_URI_PERMISSION 명시 부여
 */
object RouletteShareManager {

    private const val KAKAOTALK_PACKAGE = "com.kakao.talk"

    private val TELEGRAM_PACKAGES = listOf(
        "org.telegram.messenger",
        "org.telegram.messenger.web",
        "org.thunderdog.challegram"
    )

    private val FALLBACK_SMS_PACKAGES = listOf(
        "com.samsung.android.messaging",
        "com.google.android.apps.messaging",
        "com.android.mms"
    )

    fun buildShareSummaryText(result: RouletteSpinResult, isVideo: Boolean): String {
        val effectNote = if (result.appliedSpecialEffect != RouletteSpecialEffect.NONE) {
            " (${result.appliedSpecialEffect.title} 적용)"
        } else {
            ""
        }
        val secretLine = if (result.winnerItem.secretNote.isNotBlank()) {
            "\n당첨 메모: ${result.winnerItem.secretNote}"
        } else {
            ""
        }
        val mediaTypeLabel = if (isVideo) "회전 전 과정 영상" else "추첨 결과 카드"
        return String.format(
            Locale.KOREA,
            "[미니게임 - 만능 룰렛 & 골프 복불복 · %s] %s\n최종 결정: %s (확률 %.1f%%)%s%s",
            result.presetSnapshot.title,
            mediaTypeLabel,
            result.winnerItem.label,
            result.winnerProbabilityPercent,
            effectNote,
            secretLine
        )
    }

    /**
     * 전체 미니게임 공통 결과 이미지·전 과정 영상 요약 텍스트 생성
     */
    fun buildCommonGameShareSummaryText(payload: CommonGameSharePayload, isVideo: Boolean): String {
        val mediaTypeLabel = if (isVideo) "게임 진행 전 과정 영상" else "게임 결과 카드"
        val topLine = "[미니게임 - 만능 룰렛 & 골프 복불복 · ${payload.resultTitle}] $mediaTypeLabel"
        val rowLines = payload.rows.take(6).joinToString("\n") { row ->
            "• ${row.badgeText}: ${row.playerName} (${row.detailText})"
        }
        return if (rowLines.isNotBlank()) {
            "$topLine\n$rowLines"
        } else {
            "$topLine\n${payload.resultSubtitle}"
        }
    }

    fun getUriForFile(context: Context, file: File): Uri {
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    fun shareCommonGameMediaFile(
        context: Context,
        file: File,
        mimeType: String,
        payload: CommonGameSharePayload,
        isVideo: Boolean,
        channel: RouletteShareChannel
    ) {
        val summaryText = buildCommonGameShareSummaryText(payload, isVideo = isVideo)
        dispatchShareSummaryAndFile(
            context = context,
            file = file,
            mimeType = mimeType,
            summaryText = summaryText,
            chooserTitle = "${payload.gameType.title} 결과 공유하기",
            channel = channel
        )
    }

    fun shareMediaFile(
        context: Context,
        file: File,
        mimeType: String,
        result: RouletteSpinResult,
        isVideo: Boolean,
        channel: RouletteShareChannel
    ) {
        val summaryText = buildShareSummaryText(result, isVideo = isVideo)
        dispatchShareSummaryAndFile(
            context = context,
            file = file,
            mimeType = mimeType,
            summaryText = summaryText,
            chooserTitle = "룰렛 결과 공유하기",
            channel = channel
        )
    }

    private fun dispatchShareSummaryAndFile(
        context: Context,
        file: File,
        mimeType: String,
        summaryText: String,
        chooserTitle: String,
        channel: RouletteShareChannel
    ) {
        if (!file.exists() || file.length() <= 0L) {
            Toast.makeText(context, "공유할 미디어 파일을 찾을 수 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        val uri = runCatching { getUriForFile(context, file) }.getOrElse {
            Toast.makeText(context, "파일 공유 권한 생성에 실패했습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        val targetPackage: String? = when (channel) {
            RouletteShareChannel.KAKAOTALK ->
                if (isPackageInstalled(context, KAKAOTALK_PACKAGE)) KAKAOTALK_PACKAGE else null
            RouletteShareChannel.TELEGRAM ->
                TELEGRAM_PACKAGES.firstOrNull { isPackageInstalled(context, it) }
            RouletteShareChannel.SMS_MMS ->
                resolveDefaultSmsPackage(context)
            RouletteShareChannel.SYSTEM_MORE -> null
        }

        // 선택한 전용 메신저가 미설치된 경우 안내 토스트와 함께 시스템 공유 시트로 부드럽게 폴백
        if (channel != RouletteShareChannel.SYSTEM_MORE && targetPackage == null) {
            val appName = channel.label
            Toast.makeText(
                context,
                "${appName} 앱을 찾을 수 없어 전체 공유 창을 엽니다.",
                Toast.LENGTH_SHORT
            ).show()
            launchChooserShare(context, uri, file.name, mimeType, summaryText, chooserTitle)
            return
        }

        if (targetPackage != null) {
            val isKakao = targetPackage == KAKAOTALK_PACKAGE
            if (isKakao) {
                // 카카오톡은 이미지/영상 스트림과 EXTRA_TEXT를 함께 넣으면 텍스트만 전송되는 현상이 있으므로
                // 스트림만 안전하게 전달하고 요약 문구는 클립보드에 보조 복사해 필요 시 붙여넣을 수 있게 지원
                copyTextQuietly(context, summaryText)
            }

            val directIntent = buildSafeSendIntent(
                uri = uri,
                displayName = file.name,
                mimeType = mimeType,
                captionText = if (isKakao) null else summaryText,
                targetPackage = targetPackage,
                isContextActivity = context is Activity
            )

            grantReadUriToPackageAndResolvers(context, directIntent, uri, targetPackage)

            runCatching {
                context.startActivity(directIntent)
            }.onFailure {
                launchChooserShare(context, uri, file.name, mimeType, summaryText, chooserTitle)
            }
        } else {
            launchChooserShare(context, uri, file.name, mimeType, summaryText, chooserTitle)
        }
    }

    private fun launchChooserShare(
        context: Context,
        uri: Uri,
        displayName: String,
        mimeType: String,
        captionText: String,
        chooserTitle: String = "결과 공유하기"
    ) {
        val baseIntent = buildSafeSendIntent(
            uri = uri,
            displayName = displayName,
            mimeType = mimeType,
            captionText = captionText,
            targetPackage = null,
            isContextActivity = context is Activity
        )
        grantReadUriToPackageAndResolvers(context, baseIntent, uri, null)

        val chooserClip = ClipData(
            ClipDescription(displayName, arrayOf(mimeType)),
            ClipData.Item(uri)
        )
        val chooser = Intent.createChooser(baseIntent, chooserTitle).apply {
            clipData = chooserClip
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (context !is Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        runCatching {
            context.startActivity(chooser)
        }.onFailure {
            Toast.makeText(context, "공유할 수 있는 앱이 없습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * NullPointerException 없이 ClipDescription과 ClipData.Item으로 안전하게 SEND 인텐트를 생성하는 순수 빌더
     */
    fun buildSafeSendIntent(
        uri: Uri,
        displayName: String,
        mimeType: String,
        captionText: String?,
        targetPackage: String?,
        isContextActivity: Boolean
    ): Intent {
        val safeClip = ClipData(
            ClipDescription(displayName, arrayOf(mimeType)),
            ClipData.Item(uri)
        )
        return Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            if (!captionText.isNullOrBlank()) {
                putExtra(Intent.EXTRA_TEXT, captionText)
                putExtra("sms_body", captionText)
            }
            if (!targetPackage.isNullOrBlank()) {
                setPackage(targetPackage)
            }
            clipData = safeClip
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (!isContextActivity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }

    private fun grantReadUriToPackageAndResolvers(
        context: Context,
        intent: Intent,
        uri: Uri,
        explicitPackage: String?
    ) {
        if (!explicitPackage.isNullOrBlank()) {
            runCatching {
                context.grantUriPermission(
                    explicitPackage,
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
        }
        runCatching {
            val matches = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.queryIntentActivities(
                    intent,
                    PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            }
            matches.forEach { info ->
                val pkg = info.activityInfo?.packageName
                if (!pkg.isNullOrBlank()) {
                    context.grantUriPermission(pkg, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }
        }
    }

    private fun resolveDefaultSmsPackage(context: Context): String? {
        val systemDefault = runCatching { Telephony.Sms.getDefaultSmsPackage(context) }.getOrNull()
        if (!systemDefault.isNullOrBlank() && isPackageInstalled(context, systemDefault)) {
            return systemDefault
        }
        return FALLBACK_SMS_PACKAGES.firstOrNull { isPackageInstalled(context, it) }
    }

    private fun isPackageInstalled(context: Context, packageName: String): Boolean {
        return runCatching {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    packageName,
                    PackageManager.PackageInfoFlags.of(0L)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(packageName, 0)
            }
            true
        }.getOrDefault(false)
    }

    private fun copyTextQuietly(context: Context, text: String) {
        runCatching {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            cm?.setPrimaryClip(ClipData.newPlainText("룰렛 결과 요약", text))
        }
    }
}
