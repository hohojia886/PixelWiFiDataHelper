package io.github.hohojia886.pixelwifidatahelper

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.materialswitch.MaterialSwitch
import io.github.hohojia886.pixelwifidatahelper.utils.Constants.ACTION_UPDATE
import io.github.hohojia886.pixelwifidatahelper.utils.Constants.KEY_DATA_FIX
import io.github.hohojia886.pixelwifidatahelper.utils.Constants.KEY_WIFI_FIX
import io.github.hohojia886.pixelwifidatahelper.utils.Constants.PERMISSION_UPDATE
import io.github.hohojia886.pixelwifidatahelper.utils.Constants.PREFS_NAME

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val ctx = if (isDeviceProtectedStorage) this else createDeviceProtectedStorageContext()
        // Migrate prefs to device protected storage if needed
        runCatching {
            ctx.moveSharedPreferencesFrom(this, PREFS_NAME)
        }
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        val swWifi = findViewById<MaterialSwitch>(R.id.switch_wifi)
        val swData = findViewById<MaterialSwitch>(R.id.switch_data)
        val tvVersion = findViewById<TextView>(R.id.text_version)

        swWifi.isChecked = prefs.getBoolean(KEY_WIFI_FIX, true)
        swData.isChecked = prefs.getBoolean(KEY_DATA_FIX, true)
        
        tvVersion.text = getString(R.string.version_display, BuildConfig.VERSION_NAME)

        swWifi.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_WIFI_FIX, isChecked).apply()
            sendBroadcast(KEY_WIFI_FIX, isChecked)
        }

        swData.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_DATA_FIX, isChecked).apply()
            sendBroadcast(KEY_DATA_FIX, isChecked)
        }
    }

    @SuppressLint("WrongConstant")
    private fun sendBroadcast(key: String, value: Boolean) {
        val intent = Intent(ACTION_UPDATE)
        intent.setPackage("com.android.systemui")
        intent.putExtra(key, value)
        intent.addFlags(0x01000000)
        sendBroadcast(intent, PERMISSION_UPDATE)
    }
}
