package io.github.hohojia886.pixelwifidatahelper

import io.github.hohojia886.pixelwifidatahelper.hooks.WifiHook
import io.github.hohojia886.pixelwifidatahelper.hooks.DataHook
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import android.util.Log

class MainHook : XposedModule() {

    override fun onPackageLoaded(param: XposedModuleInterface.PackageLoadedParam) {
        super.onPackageLoaded(param)
        
        if (param.packageName == "com.android.systemui") {
            Log.i("PixelWiFiFix", "SystemUI loaded, applying hooks")
            try {
                WifiHook.hook(this, param.defaultClassLoader)
                DataHook.hook(this, param.defaultClassLoader)
            } catch (t: Throwable) {
                Log.e("PixelWiFiFix", "Failed to apply hooks", t)
            }
        }
    }
}
