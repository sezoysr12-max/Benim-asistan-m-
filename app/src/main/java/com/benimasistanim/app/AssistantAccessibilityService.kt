package com.benimasistanim.app

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

data class ListingData(
    val title: String,
    val description: String,
    val price: String,
    val imageUri: Uri?
)

object AutomationBus {
    @Volatile var pendingListing: ListingData? = null
    @Volatile var pendingCommand: String? = null
    @Volatile var service: AssistantAccessibilityService? = null

    fun startListing(data: ListingData) {
        pendingListing = data
        service?.startSahibindenListing()
    }

    fun runCommand(command: String) {
        pendingCommand = command
        service?.handleVoiceCommand(command)
    }
}

class AssistantAccessibilityService : AccessibilityService() {
    private val scope = CoroutineScope(Dispatchers.Main.immediate)

    override fun onServiceConnected() {
        super.onServiceConnected()
        AutomationBus.service = this
    }

    override fun onDestroy() {
        AutomationBus.service = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (AutomationBus.pendingCommand != null) {
            val command = AutomationBus.pendingCommand
            AutomationBus.pendingCommand = null
            if (!command.isNullOrBlank()) handleVoiceCommand(command)
        }
        val listing = AutomationBus.pendingListing
        if (listing != null && event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            scope.launch { automateListingStep(listing) }
        }
    }

    override fun onInterrupt() = Unit

    fun handleVoiceCommand(command: String) {
        val c = command.lowercase(java.util.Locale("tr", "TR"))
        when {
            c.contains("instagram") && (c.contains("gir") || c.contains("aç")) ->
                launchPackage("com.instagram.android")
            c.contains("sahibinden") && (c.contains("gir") || c.contains("aç")) ->
                launchPackage("com.sahibinden")
            c.contains("ana sayfa") || c == "eve git" ->
                performGlobalAction(GLOBAL_ACTION_HOME)
            else ->
                Toast.makeText(this, "Komut alındı: $command", Toast.LENGTH_SHORT).show()
        }
    }

    fun startSahibindenListing() {
        val data = AutomationBus.pendingListing
        if (data?.imageUri != null) {
            try {
                val share = Intent(Intent.ACTION_SEND).apply {
                    type = "image/*"
                    putExtra(Intent.EXTRA_STREAM, data.imageUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    setPackage("com.sahibinden")
                }
                startActivity(share)
                return
            } catch (_: Exception) {
                // Sahibinden sürümü paylaşım hedefini desteklemiyorsa normal uygulama açılışına düş.
            }
        }
        launchPackage("com.sahibinden")
    }

    private fun launchPackage(packageName: String) {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        if (intent == null) {
            Toast.makeText(this, "Uygulama bulunamadı: $packageName", Toast.LENGTH_LONG).show()
            return
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    private var lastStepAt = 0L
    private var lastScreenSignature = ""

    private suspend fun automateListingStep(data: ListingData) {
        val now = System.currentTimeMillis()
        if (now - lastStepAt < 700) return

        val root = rootInActiveWindow ?: return
        val signature = buildScreenSignature(root)
        if (signature == lastScreenSignature && now - lastStepAt < 2500) return

        lastStepAt = now
        lastScreenSignature = signature

        // Önce doğrudan düzenlenebilir alanları doldur.
        var changed = false
        changed = fillFirstEditable(root, listOf("İlan başlığı", "Başlık", "title"), data.title) || changed
        changed = fillFirstEditable(root, listOf("Açıklama", "İlan açıklaması", "description"), data.description) || changed
        changed = fillFirstEditable(root, listOf("Fiyat", "price"), data.price) || changed

        // Sahibinden farklı sürümlerde alan etiketi değişebildiği için
        // içerik açıklamasından da alan bulmayı dene.
        if (!changed) {
            fillFirstMatching(root, listOf("İlan başlığı", "Başlık", "title"), data.title)
            fillFirstMatching(root, listOf("Açıklama", "İlan açıklaması", "description"), data.description)
            fillFirstMatching(root, listOf("Fiyat", "price"), data.price)
        }

        // İleri/devam butonu görünüyorsa yalnızca ilan akışında tıklamayı dene.
        clickFirstMatching(root, listOf("Devam", "İleri", "Kaydet ve devam et", "Devam et"))
    }

    private fun fillFirstEditable(
        root: AccessibilityNodeInfo,
        labels: List<String>,
        value: String
    ): Boolean {
        val node = findNode(root, labels) ?: return false
        if (!node.isEditable) return false
        val args = Bundle().apply {
            putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                value
            )
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    private fun clickFirstMatching(
        root: AccessibilityNodeInfo,
        labels: List<String>
    ): Boolean {
        for (label in labels) {
            val nodes = root.findAccessibilityNodeInfosByText(label)
            val target = nodes.firstOrNull { it.isClickable } ?: nodes.firstOrNull()
            if (target != null) {
                var node: AccessibilityNodeInfo? = target
                while (node != null) {
                    if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                        return true
                    }
                    node = node.parent
                }
            }
        }
        return false
    }

    private fun buildScreenSignature(root: AccessibilityNodeInfo): String {
        val values = mutableListOf<String>()
        collectText(root, values, 0)
        return values.take(80).joinToString("|")
    }

    private fun collectText(node: AccessibilityNodeInfo, values: MutableList<String>, depth: Int) {
        if (depth > 8 || values.size >= 100) return
        node.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { values.add(it) }
        node.contentDescription?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { values.add(it) }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { collectText(it, values, depth + 1) }
        }
    }

    private fun fillFirstMatching(
        root: AccessibilityNodeInfo,
        labels: List<String>,
        value: String
    ): Boolean {
        val node = findNode(root, labels) ?: return false
        val args = Bundle()
        args.putCharSequence(
            AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
            value
        )
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    private fun findNode(root: AccessibilityNodeInfo, labels: List<String>): AccessibilityNodeInfo? {
        for (label in labels) {
            val exact = root.findAccessibilityNodeInfosByText(label)
            if (exact.isNotEmpty()) {
                return exact.firstOrNull { it.isEditable } ?: exact.first()
            }
        }
        return null
    }
}