package com.hmgchallenge.pleosstarter

import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.constant.area.DoorValue

class MainActivity : Activity() {
    private var vehicle: Vehicle? = null
    private lateinit var result: TextView
    private lateinit var readButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val padding = (24 * resources.displayMetrics.density).toInt()
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
        }
        content.addView(TextView(this).apply {
            setText(R.string.heading)
            textSize = 26f
        })
        content.addView(TextView(this).apply {
            setText(R.string.description)
            textSize = 18f
            setPadding(0, padding, 0, padding)
        })
        readButton = Button(this).apply {
            setText(R.string.read_door)
            setOnClickListener { requestDoorState() }
        }
        content.addView(readButton)
        result = TextView(this).apply {
            setText(R.string.waiting)
            textSize = 18f
            setPadding(0, padding, 0, 0)
        }
        content.addView(result)
        setContentView(content)
    }

    private fun requestDoorState() {
        if (checkSelfPermission(DOOR_PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(DOOR_PERMISSION), PERMISSION_REQUEST)
            return
        }
        readDoorState()
    }

    private fun readDoorState() {
        readButton.isEnabled = false
        result.setText(R.string.loading)
        try {
            val connectedVehicle = vehicle ?: Vehicle(applicationContext).also {
                vehicle = it
                it.initialize()
            }
            connectedVehicle.getDoor().getCurrentDoorPosition(
                area = DoorArea(DoorValue.ROW_1_LEFT),
                onComplete = { position: Int? ->
                    showResult(
                        position?.let { getString(R.string.door_result, it) }
                            ?: getString(R.string.no_value)
                    )
                },
                onFailed = { error: Exception -> showError(error) }
            )
        } catch (error: Exception) {
            vehicle?.release()
            vehicle = null
            showError(error)
        }
    }

    private fun showError(error: Exception) {
        showResult(getString(R.string.sdk_error, error.message ?: error.javaClass.simpleName))
    }

    private fun showResult(message: String) {
        runOnUiThread {
            if (!isFinishing && !isDestroyed) {
                result.text = message
                readButton.isEnabled = true
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                readDoorState()
            } else {
                result.setText(R.string.permission_denied)
            }
        }
    }

    override fun onDestroy() {
        vehicle?.release()
        vehicle = null
        super.onDestroy()
    }

    companion object {
        private const val DOOR_PERMISSION = "pleos.car.permission.CAR_DOORS"
        private const val PERMISSION_REQUEST = 100
    }
}
