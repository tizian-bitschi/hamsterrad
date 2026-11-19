package de.tizian.hamsterrad

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner

/** Ob gerade irgendein Bildschirm der App sichtbar ist. */
object AppVisible {

    var visible = false
        private set

    private var watching = false

    fun watch() {
        if (watching) return
        watching = true
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    visible = true
                }

                override fun onStop(owner: LifecycleOwner) {
                    visible = false
                }
            },
        )
    }
}
