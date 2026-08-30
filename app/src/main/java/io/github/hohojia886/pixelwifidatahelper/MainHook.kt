package io.github.hohojia886.pixelwifidatahelper

import io.github.hohojia886.pixelwifidatahelper.hooks.WifiHook
import io.github.hohojia886.pixelwifidatahelper.hooks.DataHook
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import android.util.Log
import android.os.Build
import io.github.hohojia886.pixelwifidatahelper.utils.Constants.LOG_TAG

class MainHook : XposedModule() {

    override fun onPackageLoaded(param: XposedModuleInterface.PackageLoadedParam) {
        super.onPackageLoaded(param)
        
        if (param.packageName == "com.android.systemui") {
            Log.i(LOG_TAG, "SystemUI loaded (SDK ${Build.VERSION.SDK_INT}), applying hooks")
            
            try {
                WifiHook.hook(this, param.defaultClassLoader)
                DataHook.hook(this, param.defaultClassLoader)
            } catch (t: Throwable) {
                Log.e(LOG_TAG, "Failed to apply hooks", t)
            }
        }
    }
}
