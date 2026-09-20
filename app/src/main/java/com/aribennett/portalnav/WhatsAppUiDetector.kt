package com.aribennett.portalnav

import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo

data class CallerIdentity(
    val displayName: String?,
    val phoneNumber: String?
)

data class IncomingCallUi(
    val caller: CallerIdentity,
    val answerNode: AccessibilityNodeInfo
)

object WhatsAppUiDetector {
    fun detectIncoming(root: AccessibilityNodeInfo?): IncomingCallUi? {
        if (root == null) return null
        val nodes = flatten(root)
        val answer = nodes.firstOrNull { isAnswerControl(it) } ?: return null
        val caller = extractCaller(nodes)
        return IncomingCallUi(caller, answer)
    }

    fun isActiveCall(root: AccessibilityNodeInfo?): Boolean {
        if (root == null) return false
        return flatten(root).any { node ->
            val text = node.textString().lowercase()
            val desc = node.descString().lowercase()
            text == "end" ||
                desc == "end call" ||
                desc == "end" ||
                text.contains("mute") ||
                desc.contains("mute")
        }
    }

    private fun isAnswerControl(node: AccessibilityNodeInfo): Boolean {
        if (!node.isClickable) return false
        val text = node.textString().lowercase()
        val desc = node.descString().lowercase()
        val id = node.viewIdResourceName.orEmpty().lowercase()
        return text == "answer" ||
            desc == "answer" ||
            desc.contains("answer") ||
            id.contains("answer") ||
            id.contains("accept")
    }

    private fun extractCaller(nodes: List<AccessibilityNodeInfo>): CallerIdentity {
        var phone: String? = null
        var name: String? = null
        for (node in nodes) {
            val values = listOf(node.textString(), node.descString())
            for (raw in values) {
                val value = raw.trim()
                if (value.isBlank()) continue
                if (phone == null && looksLikePhone(value)) phone = value
                if (name == null && looksLikeName(value)) name = value
            }
        }
        return CallerIdentity(name, phone)
    }

    private fun looksLikePhone(value: String): Boolean {
        val digits = value.count { it.isDigit() }
        return digits >= 7 && value.any { it == '+' || it == '(' || it == '-' || it.isDigit() }
    }

    private fun looksLikeName(value: String): Boolean {
        val lower = value.lowercase()
        if (value.length !in 2..80) return false
        if (looksLikePhone(value)) return false
        if (lower in setOf("answer", "decline", "end", "mute", "speaker", "whatsapp")) return false
        if (lower.contains("incoming") || lower.contains("voice call") || lower.contains("video call")) return false
        return value.any { it.isLetter() }
    }

    private fun flatten(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val out = ArrayList<AccessibilityNodeInfo>()
        fun visit(node: AccessibilityNodeInfo?) {
            if (node == null) return
            out += node
            for (i in 0 until node.childCount) visit(node.getChild(i))
        }
        visit(root)
        return out
    }

    private fun AccessibilityNodeInfo.textString(): String = text?.toString().orEmpty()

    private fun AccessibilityNodeInfo.descString(): String = contentDescription?.toString().orEmpty()
}
