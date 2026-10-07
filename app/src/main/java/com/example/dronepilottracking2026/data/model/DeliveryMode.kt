package com.example.dronepilottracking2026.data.model

enum class DeliveryMode {
    INTERNET,
    DMR
}

fun DeliveryMode.label(): String = when (this) {
    DeliveryMode.INTERNET -> "INTERNET"
    DeliveryMode.DMR -> "DMR"
}

fun String.toDeliveryMode(): DeliveryMode = when (trim().uppercase()) {
    DeliveryMode.DMR.name -> DeliveryMode.DMR
    else -> DeliveryMode.INTERNET
}

const val DEFAULT_DMR_INTERVAL_MS = 5_000L
const val DEFAULT_DMR_SLOT = 1
const val DMR_SLOT_COUNT = 6
const val DMR_SLOT_SPACING_MS = 1_500L
const val DMR_CYCLE_MS = DMR_SLOT_COUNT * DMR_SLOT_SPACING_MS
const val DMR_SLOT_GRACE_MS = 750L
const val DMR_SOS_REPEAT_INTERVAL_MS = 30_000L
const val DMR_MOVEMENT_THRESHOLD_METERS = 20f
const val DMR_SEQUENCE_MAX = 299L

fun nextDmrSequenceValue(current: Long): Long =
    if (current >= DMR_SEQUENCE_MAX) 0L else current.coerceAtLeast(0L) + 1L

fun isDmrSlotWindow(now: Long, slot: Int): Boolean {
    val offset = Math.floorMod(now, DMR_CYCLE_MS)
    val slotOffset = (slot.coerceIn(1, DMR_SLOT_COUNT) - 1) * DMR_SLOT_SPACING_MS
    return offset in slotOffset..(slotOffset + DMR_SLOT_GRACE_MS)
}
