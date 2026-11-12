package com.hmgchallenge.pleosstarter.driving

import android.car.Car
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context
import android.os.Handler
import android.os.Looper

/** Loaded only on Automotive. Unknown/restricted state locks detailed learning and settings. */
class CarDetailSource(private val context: Context, private val onAllowed: (Boolean) -> Unit) {
    private var car: Car? = null
    private var manager: CarUxRestrictionsManager? = null
    private var stopped = false
    private val listener = CarUxRestrictionsManager.OnUxRestrictionsChangedListener {
        if (!stopped) onAllowed(!it.isRequiresDistractionOptimization)
    }
    fun start() {
        stopped = false; onAllowed(false)
        car = Car.createCar(context, Handler(Looper.getMainLooper()), Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT) { connected, ready ->
            if (stopped) return@createCar
            if (!ready) { manager = null; onAllowed(false) }
            else try {
                manager = connected.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as CarUxRestrictionsManager
                manager?.registerListener(listener)
                onAllowed(manager?.currentCarUxRestrictions?.isRequiresDistractionOptimization == false)
            } catch (_: Exception) { onAllowed(false) }
        }
    }
    fun stop() {
        stopped = true
        runCatching { manager?.unregisterListener() }; runCatching { car?.disconnect() }
        manager = null; car = null; onAllowed(false)
    }
}
