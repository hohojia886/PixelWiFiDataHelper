package io.github.hohojia886.pixelwifidatahelper.hooks

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.net.wifi.WifiManager
import android.util.Log
import io.github.hohojia886.pixelwifidatahelper.utils.Constants.ACTION_UPDATE
import io.github.hohojia886.pixelwifidatahelper.utils.Constants.KEY_WIFI_FIX
import io.github.hohojia886.pixelwifidatahelper.utils.Constants.LOG_TAG
import io.github.hohojia886.pixelwifidatahelper.utils.Constants.PERMISSION_UPDATE
import io.github.hohojia886.pixelwifidatahelper.utils.Constants.PROVIDER_URI
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Field
import java.lang.reflect.Method

object WifiHook {
    private const val TARGET_CLASS = "com.android.systemui.statusbar.pipeline.wifi.data.repository.prod.WifiRepositoryImpl"
    @Volatile private var isEnabled = true

    private var cancelMethod: Method? = null
    private var wifiManagerField: Field? = null

    private fun updateState(context: Context) {
        Log.d(LOG_TAG, "WifiHook: Attempting to load state from provider...")
        runCatching {
            val uri = Uri.parse(PROVIDER_URI)
            val bundle = context.contentResolver.call(uri, "get", null, null)
            if (bundle != null) {
                isEnabled = bundle.getBoolean(KEY_WIFI_FIX, true)
                Log.i(LOG_TAG, "WifiHook: Status loaded from provider: $isEnabled")
            } else {
                Log.e(LOG_TAG, "WifiHook: Failed to load status: bundle is null.")
            }
        }.onFailure {
            Log.e(LOG_TAG, "WifiHook: Critical error loading status from provider", it)
        }
    }

    fun hook(module: XposedModule, classLoader: ClassLoader) {
        try {
            val wifiRepoClass = classLoader.loadClass(TARGET_CLASS)
            val pauseWifiMethod = wifiRepoClass.getDeclaredMethod("pauseWifi")

            runCatching {
                cancelMethod = wifiRepoClass.getDeclaredMethod("cancelOptimisticToggleTimeoutJobs").apply { isAccessible = true }
                wifiManagerField = wifiRepoClass.getDeclaredField("wifiManager").apply { isAccessible = true }
            }.onFailure {
                Log.e(LOG_TAG, "WifiHook: Failed to find internal methods/fields", it)
            }

            // Update receiver
            runCatching {
                val appClass = classLoader.loadClass("android.app.Application")
                module.hook(appClass.getDeclaredMethod("onCreate")).intercept { chain ->
                    val app = chain.thisObject as? Context
                    if (app != null) {
                        // Initial load
                        updateState(app)

                        val filter = IntentFilter(ACTION_UPDATE)
                        app.registerReceiver(object : BroadcastReceiver() {
                            override fun onReceive(context: Context?, intent: Intent?) {
                                if (intent != null && intent.hasExtra(KEY_WIFI_FIX)) {
                                    isEnabled = intent.getBooleanExtra(KEY_WIFI_FIX, true)
                                    Log.i(LOG_TAG, "WifiHook: WiFi fix status updated by broadcast: $isEnabled")
                                }
                            }
                        }, filter, PERMISSION_UPDATE, null, Context.RECEIVER_EXPORTED)
                    }
                    chain.proceed()
                }
            }

            module.hook(pauseWifiMethod).intercept { chain ->
                if (!isEnabled) {
                    return@intercept chain.proceed()
                }

                val instance = chain.thisObject ?: return@intercept chain.proceed()
                
                val success = runCatching {
                    cancelMethod?.invoke(instance)
                    
                    val wifiManager = wifiManagerField?.get(instance) as? WifiManager
                        ?: return@runCatching false

                    Log.i(LOG_TAG, "WifiHook: Forcing setWifiEnabled(false)")
                    @Suppress("DEPRECATION")
                    wifiManager.setWifiEnabled(false)
                    true
                }.getOrElse {
                    Log.e(LOG_TAG, "WifiHook: Error in pauseWifi hook", it)
                    false
                }
                
                if (success) null else chain.proceed()
            }
        } catch (e: Throwable) {
            Log.e(LOG_TAG, "WifiHook: Failed to hook pauseWifi", e)
        }
    }
}
