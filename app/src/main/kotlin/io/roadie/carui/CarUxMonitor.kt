package io.roadie.carui

import android.content.Context
import java.lang.reflect.Proxy

/**
 * Reflection-based AAOS CarUxRestrictionsManager listener.
 * Compiles cleanly against standard Android SDKs while automatically enforcing
 * audio-only mode when an AAOS vehicle shifts out of Park.
 */
class CarUxMonitor(
    private val context: Context,
    private val onDrivingRestrictionChanged: (restricted: Boolean) -> Unit
) {
    private var carInstance: Any? = null
    private var uxManager: Any? = null

    fun start() {
        runCatching {
            val carClass = Class.forName("android.car.Car")
            val createCar = carClass.getMethod("createCar", Context::class.java)
            val car = createCar.invoke(null, context.applicationContext) ?: return
            carInstance = car

            val getCarManager = carClass.getMethod("getCarManager", String::class.java)
            val manager = getCarManager.invoke(car, "uxrestriction") ?: return
            uxManager = manager

            val uxManagerClass = Class.forName("android.car.drivingstate.CarUxRestrictionsManager")
            val restrictionsClass = Class.forName("android.car.drivingstate.CarUxRestrictions")
            val listenerClass = Class.forName(
                "android.car.drivingstate.CarUxRestrictionsManager\$OnUxRestrictionsChangedListener"
            )
            val isRestrictedMethod = restrictionsClass.getMethod("isRequiresDistractionOptimization")

            val current = uxManagerClass.getMethod("getCurrentCarUxRestrictions").invoke(manager)
            if (current != null) {
                val restricted = isRestrictedMethod.invoke(current) as? Boolean ?: false
                onDrivingRestrictionChanged(restricted)
            }

            val proxy = Proxy.newProxyInstance(
                listenerClass.classLoader,
                arrayOf(listenerClass)
            ) { _, method, args ->
                if (method.name == "onUxRestrictionsChanged" && args?.isNotEmpty() == true && args[0] != null) {
                    val restricted = isRestrictedMethod.invoke(args[0]) as? Boolean ?: false
                    onDrivingRestrictionChanged(restricted)
                }
                null
            }

            uxManagerClass.getMethod("registerListener", listenerClass).invoke(manager, proxy)
        }
    }

    fun stop() {
        runCatching {
            val manager = uxManager
            if (manager != null) {
                val uxManagerClass = Class.forName("android.car.drivingstate.CarUxRestrictionsManager")
                uxManagerClass.getMethod("unregisterListener").invoke(manager)
            }
            val car = carInstance
            if (car != null) {
                Class.forName("android.car.Car").getMethod("disconnect").invoke(car)
            }
        }
        uxManager = null
        carInstance = null
    }
}
