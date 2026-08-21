package io.github.hohojia886.pixelwifidatahelper.hooks

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.net.wifi.WifiManager
import android.util.Log
import io.github.libxposed.api.XposedModule

object WifiHook {
    private const val TARGET_CLASS = "com.android.systemui.statusbar.pipeline.wifi.data.repository.prod.WifiRepositoryImpl"
    @Volatile private var isEnabled = true
    private var isInitialized = false

    private fun updateState(context: Context) {
        Log.d("PixelWiFiFix", "Attempting to load state from provider... context: ${context.packageName}")
        runCatching {
            val uri = Uri.parse("content://io.github.hohojia886.pixelwifidatahelper.prefs")
            val bundle = context.contentResolver.call(uri, "get", null, null)
            if (bundle != null) {
                isEnabled = bundle.getBoolean("wifi_fix", true)
                isInitialized = true
                Log.i("PixelWiFiFix", "Status loaded from provider: $isEnabled (Total keys: ${bundle.size()})")
            } else {
                Log.e("PixelWiFiFix", "Failed to load status: bundle is null. Is the provider exported and authority correct?")
            }
        }.onFailure {
            Log.e("PixelWiFiFix", "Critical error loading status from provider: ${it.message}", it)
        }
    }

    fun hook(module: XposedModule, classLoader: ClassLoader) {
        try {
            // Update receiver
            runCatching {
                val appClass = classLoader.loadClass("android.app.Application")
                module.hook(appClass.getDeclaredMethod("onCreate")).intercept { chain ->
                    val app = chain.thisObject as? Context
                    if (app != null) {
                        // Initial load
                        updateState(app)

                        val filter = IntentFilter("io.github.hohojia886.pixelwifidatahelper.UPDATE")
                        app.registerReceiver(object : BroadcastReceiver() {
                            override fun onReceive(context: Context?, intent: Intent?) {
                                if (intent != null && intent.hasExtra("wifi_fix")) {
                                    isEnabled = intent.getBooleanExtra("wifi_fix", true)
                                    isInitialized = true
                                    Log.i("PixelWiFiFix", "WiFi fix status updated by broadcast: $isEnabled")
                                }
                            }
                        }, filter, "io.github.hohojia886.pixelwifidatahelper.PERMISSION", null, Context.RECEIVER_EXPORTED)
                    }
                    chain.proceed()
                }
            }

            val wifiRepoClass = classLoader.loadClass(TARGET_CLASS)
            val pauseWifiMethod = wifiRepoClass.getDeclaredMethod("pauseWifi")

            module.hook(pauseWifiMethod).intercept { chain ->
                if (!isEnabled) {
                    return@intercept chain.proceed()
                }

                val instance = chain.thisObject
                if (instance == null) {
                    return@intercept chain.proceed()
                }
                
                try {
                    runCatching {
                        val cancelMethod = instance.javaClass.getDeclaredMethod("cancelOptimisticToggleTimeoutJobs")
                        cancelMethod.isAccessible = true
                        cancelMethod.invoke(instance)
                    }

                    val wifiManagerField = instance.javaClass.getDeclaredField("wifiManager")
                    wifiManagerField.isAccessible = true
                    val wifiManager = wifiManagerField.get(instance) as? WifiManager

                    if (wifiManager != null) {
                        Log.i("PixelWiFiFix", "WiFi Fix Active: Forcing setWifiEnabled(false)")
                        @Suppress("DEPRECATION")
                        wifiManager.setWifiEnabled(false)
                    }
                } catch (e: Exception) {
                    Log.e("PixelWiFiFix", "Error in pauseWifi hook", e)
                }
                
                null 
            }
        } catch (e: Throwable) {
            Log.e("PixelWiFiFix", "Failed to hook pauseWifi", e)
        }
    }
}
