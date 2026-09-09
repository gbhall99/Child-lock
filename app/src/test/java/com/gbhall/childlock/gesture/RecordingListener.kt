package com.gbhall.childlock.gesture

class RecordingListener : GestureListener {
    val events = mutableListOf<GestureEvent>()
    override fun onGestureEvent(event: GestureEvent) { events += event }
    val unlocked: Boolean get() = GestureEvent.Unlocked in events
    val resets: Int get() = events.count { it is GestureEvent.Reset }
    fun clear() = events.clear()
}
