package com.hmgchallenge.pleosstarter.quests

import android.car.Car
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context
import android.os.Handler
import android.os.Looper

/** This prototype is a parked learning screen, not a distraction-optimized driving app. */
class QuestDrivingGuard(context: Context, private val onRestricted: (Boolean) -> Unit) {
    private var car: Car? = null
    private var manager: CarUxRestrictionsManager? = null
    private var stopped = false
    private val listener = CarUxRestrictionsManager.OnUxRestrictionsChangedListener { restriction ->
        if (!stopped) onRestricted(restriction.isRequiresDistractionOptimization)
    }
    init {
        onRestricted(true) // Unknown automotive state must not be treated as parked.
        try {
            car = Car.createCar(context, Handler(Looper.getMainLooper()), Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT) { connected, ready ->
                if (!stopped) {
                    if (!ready) onRestricted(true)
                    else try {
                        val current = connected.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as CarUxRestrictionsManager
                        manager = current
                        current.registerListener(listener)
                        onRestricted(current.currentCarUxRestrictions.isRequiresDistractionOptimization)
                    } catch (_: Exception) { onRestricted(true) }
                }
            }
        } catch (_: Exception) { onRestricted(true) }
    }
    fun stop() {
        stopped = true
        runCatching { manager?.unregisterListener() }; manager = null
        runCatching { car?.disconnect() }; car = null
    }
}
