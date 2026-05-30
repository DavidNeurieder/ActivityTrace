package com.activitytrace.capture

import android.app.PendingIntent
import android.content.IntentSender
import android.os.Parcel
import android.util.Base64

fun PendingIntent.serialize(): String? {
    val parcel = Parcel.obtain()
    try {
        writeToParcel(parcel, 0)
        val bytes = parcel.marshall()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    } catch (_: Exception) {
        return null
    } finally {
        parcel.recycle()
    }
}

fun String.deserializeToPendingIntent(): PendingIntent? {
    val parcel = Parcel.obtain()
    try {
        val bytes = Base64.decode(this, Base64.NO_WRAP)
        parcel.unmarshall(bytes, 0, bytes.size)
        parcel.setDataPosition(0)
        return PendingIntent.CREATOR.createFromParcel(parcel)
    } catch (_: Exception) {
        return null
    } finally {
        parcel.recycle()
    }
}

fun String.deserializeToIntentSender(): IntentSender? {
    val parcel = Parcel.obtain()
    try {
        val bytes = Base64.decode(this, Base64.NO_WRAP)
        parcel.unmarshall(bytes, 0, bytes.size)
        parcel.setDataPosition(0)
        return IntentSender.CREATOR.createFromParcel(parcel)
    } catch (_: Exception) {
        return null
    } finally {
        parcel.recycle()
    }
}
