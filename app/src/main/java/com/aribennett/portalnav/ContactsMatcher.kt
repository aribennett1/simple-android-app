package com.aribennett.portalnav

import android.content.Context
import android.telephony.PhoneNumberUtils
import android.util.Log

object ContactsMatcher {
    private const val TAG = "PortalNavWA"

    fun isSavedContact(context: Context, identity: CallerIdentity): Boolean {
        if (hasWhatsAppSavedContactSignal(identity)) {
            Log.i(TAG, "Caller accepted by WhatsApp saved-contact signal")
            return true
        }

        Log.i(TAG, "Caller did not expose a WhatsApp saved-contact signal: $identity")
        return false
    }

    private fun hasWhatsAppSavedContactSignal(identity: CallerIdentity): Boolean {
        val name = identity.displayName?.trim().orEmpty()
        val phone = identity.phoneNumber?.trim().orEmpty()
        return name.isNotBlank() &&
            phone.isNotBlank() &&
            !looksLikeGenericCallerText(name) &&
            PhoneNumberUtils.normalizeNumber(name) != PhoneNumberUtils.normalizeNumber(phone)
    }

    private fun looksLikeGenericCallerText(value: String): Boolean {
        val lower = value.lowercase()
        return lower.contains("incoming") ||
            lower.contains("whatsapp") ||
            lower.contains("voice call") ||
            lower.contains("video call") ||
            lower == "answer" ||
            lower == "decline"
    }
}
