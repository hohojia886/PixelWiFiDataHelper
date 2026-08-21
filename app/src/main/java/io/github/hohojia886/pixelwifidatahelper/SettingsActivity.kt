package io.github.hohojia886.pixelwifidatahelper

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.materialswitch.MaterialSwitch

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val ctx = if (isDeviceProtectedStorage) this else createDeviceProtectedStorageContext()
        // Migrate prefs to device protected storage if needed
        runCatching {
            ctx.moveSharedPreferencesFrom(this, "pixel_wifi_data_prefs")
        }
        val prefs = ctx.getSharedPreferences("pixel_wifi_data_prefs", Context.MODE_PRIVATE)
        
        val swWifi = findViewById<MaterialSwitch>(R.id.switch_wifi)
        val swData = findViewById<MaterialSwitch>(R.id.switch_data)
        val tvVersion = findViewById<TextView>(R.id.text_version)

        swWifi.isChecked = prefs.getBoolean("wifi_fix", true)
        swData.isChecked = prefs.getBoolean("data_fix", true)
        
        tvVersion.text = getString(R.string.version_display, BuildConfig.VERSION_NAME)

        swWifi.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("wifi_fix", isChecked).apply()
            sendBroadcast("wifi_fix", isChecked)
        }

        swData.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("data_fix", isChecked).apply()
            sendBroadcast("data_fix", isChecked)
        }
    }

    @SuppressLint("WrongConstant")
    private fun sendBroadcast(key: String, value: Boolean) {
        val intent = Intent("io.github.hohojia886.pixelwifidatahelper.UPDATE")
        intent.setPackage("com.android.systemui")
        intent.putExtra(key, value)
        intent.addFlags(0x01000000)
        sendBroadcast(intent, "io.github.hohojia886.pixelwifidatahelper.PERMISSION")
    }
}
