package io.github.hohojia886.pixelwifidatahelper.hooks

import android.net.wifi.WifiManager
import android.util.Log
import io.github.libxposed.api.XposedModule

object WifiHook {
    private const val TARGET_CLASS = "com.android.systemui.statusbar.pipeline.wifi.data.repository.prod.WifiRepositoryImpl"

    fun hook(module: XposedModule, classLoader: ClassLoader) {
        try {
            val wifiRepoClass = classLoader.loadClass(TARGET_CLASS)
            val pauseWifiMethod = wifiRepoClass.getDeclaredMethod("pauseWifi")

            module.hook(pauseWifiMethod).intercept { chain ->
                val instance = chain.thisObject
                if (instance == null) {
                    Log.w("PixelWiFiFix", "pauseWifi called on null instance")
                    return@intercept chain.proceed()
                }
                
                try {
                    // 1. Cancel optimistic toggle timeout jobs
                    try {
                        val cancelMethod = instance.javaClass.getDeclaredMethod("cancelOptimisticToggleTimeoutJobs")
                        cancelMethod.isAccessible = true
                        cancelMethod.invoke(instance)
                    } catch (e: Exception) {
                        Log.w("PixelWiFiFix", "Could not call cancelOptimisticToggleTimeoutJobs", e)
                    }

                    // 2. Get wifiManager field
                    val wifiManagerField = instance.javaClass.getDeclaredField("wifiManager")
                    wifiManagerField.isAccessible = true
                    val wifiManager = wifiManagerField.get(instance) as? WifiManager

                    if (wifiManager != null) {
                        Log.i("PixelWiFiFix", "Intercepting pauseWifi, turning off WiFi instead")
                        // 3. Actually turn off WiFi
                        @Suppress("DEPRECATION")
                        wifiManager.setWifiEnabled(false)
                    } else {
                        Log.e("PixelWiFiFix", "wifiManager field is null")
                    }
                } catch (e: Exception) {
                    Log.e("PixelWiFiFix", "Error in pauseWifi hook", e)
                }
                
                // Return null and don't call chain.proceed() to skip original method
                null 
            }
            Log.i("PixelWiFiFix", "Successfully hooked pauseWifi")
        } catch (e: Throwable) {
            Log.e("PixelWiFiFix", "Failed to hook pauseWifi", e)
        }
    }
}
