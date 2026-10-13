package io.github.hohojia886.pixelwifidatahelper.hooks

import android.app.AlertDialog
import android.app.Dialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.util.Log
import android.view.View
import io.github.hohojia886.pixelwifidatahelper.utils.Constants.ACTION_UPDATE
import io.github.hohojia886.pixelwifidatahelper.utils.Constants.KEY_DATA_FIX
import io.github.hohojia886.pixelwifidatahelper.utils.Constants.LOG_TAG
import io.github.hohojia886.pixelwifidatahelper.utils.Constants.PERMISSION_UPDATE
import io.github.hohojia886.pixelwifidatahelper.utils.Constants.PROVIDER_URI
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Field

object DataHook {
    @Volatile private var isEnabled = true

    private fun updateState(context: Context) {
        Log.d(LOG_TAG, "DataHook: Attempting to load state from provider...")
        runCatching {
            val uri = Uri.parse(PROVIDER_URI)
            val bundle = context.contentResolver.call(uri, "get", null, null)
            if (bundle != null) {
                isEnabled = bundle.getBoolean(KEY_DATA_FIX, true)
                Log.i(LOG_TAG, "DataHook: Status loaded from provider: $isEnabled")
            } else {
                Log.e(LOG_TAG, "DataHook: Failed to load status: bundle is null.")
            }
        }.onFailure {
            Log.e(LOG_TAG, "DataHook: Critical error loading status from provider", it)
        }
    }

    fun hook(module: XposedModule, classLoader: ClassLoader) {
        // Register receiver and initial load on Application.onCreate
        runCatching {
            val appClass = classLoader.loadClass("android.app.Application")
            module.hook(appClass.getDeclaredMethod("onCreate")).intercept { chain ->
                val app = chain.thisObject as? Context
                if (app != null) {
                    updateState(app)

                    val filter = IntentFilter(ACTION_UPDATE)
                    app.registerReceiver(object : BroadcastReceiver() {
                        override fun onReceive(context: Context?, intent: Intent?) {
                            if (intent != null && intent.hasExtra(KEY_DATA_FIX)) {
                                isEnabled = intent.getBooleanExtra(KEY_DATA_FIX, true)
                                Log.i(LOG_TAG, "DataHook: Data fix status updated by broadcast: $isEnabled")
                            }
                        }
                    }, filter, PERMISSION_UPDATE, null, Context.RECEIVER_EXPORTED)
                }
                chain.proceed()
            }
        }.onFailure {
            Log.e(LOG_TAG, "DataHook: Failed to hook Application.onCreate", it)
        }

        // Zero-Flicker Pre-Show Dialog Hook: Intercepts android.app.Dialog#show() BEFORE window attachment
        try {
            val dialogClass = classLoader.loadClass("android.app.Dialog")
            val showMethod = dialogClass.getDeclaredMethod("show")
            
            module.hook(showMethod).intercept { chain ->
                val dialog = chain.thisObject as? Dialog

                if (isEnabled && dialog != null) {
                    // Check StackTrace BEFORE chain.proceed() to suppress window attachment entirely
                    val isMobileDataStack = Throwable().stackTrace.any { element ->
                        val cls = element.className
                        cls.contains("MobileData") || cls.contains("Cellular") || cls.contains("MobileConnections") || cls.contains("cell.domain")
                    }

                    if (isMobileDataStack) {
                        Log.i(LOG_TAG, "DataHook: MATCHED Mobile Data confirmation dialog [${dialog.javaClass.name}] PRE-SHOW -> Applying Absolute Transparency & Auto-Confirming!")
                        
                        // 1. Force Absolute Window Transparency, Zero Alpha, & Invisible Attributes BEFORE proceeding
                        runCatching {
                            dialog.window?.let { win ->
                                win.setDimAmount(0f)
                                win.setWindowAnimations(0)
                                win.attributes = win.attributes.apply {
                                    alpha = 0f
                                    dimAmount = 0f
                                }
                                win.decorView.visibility = View.INVISIBLE
                            }
                        }

                        // 2. Try triggering listener directly without rendering window
                        var triggered = false
                        runCatching {
                            if (dialog is AlertDialog) {
                                dialog.create() // Ensure AlertController is initialized
                                val alertField = findField(AlertDialog::class.java, "mAlert")
                                val alertController = alertField?.get(dialog)
                                if (alertController != null) {
                                    val listenerField = findField(alertController.javaClass, "mButtonPositiveListener")
                                    val listener = listenerField?.get(alertController) as? DialogInterface.OnClickListener
                                    if (listener != null) {
                                        listener.onClick(dialog, DialogInterface.BUTTON_POSITIVE)
                                        triggered = true
                                    }
                                }
                            }
                        }

                        if (triggered) {
                            return@intercept null // Skip chain.proceed() completely
                        }

                        // 3. Fallback: Proceed with 100% transparent window, then perform instant click & dismiss
                        val res = chain.proceed()
                        runCatching {
                            (dialog as? AlertDialog)?.getButton(DialogInterface.BUTTON_POSITIVE)?.performClick() ?: dialog.dismiss()
                        }
                        return@intercept res
                    }
                }
                
                chain.proceed()
            }
            Log.i(LOG_TAG, "DataHook: Successfully hooked Dialog#show for zero-flicker pre-show interception")
        } catch (e: Throwable) {
            Log.e(LOG_TAG, "DataHook: Failed to hook Dialog#show", e)
        }
    }

    // Helper: Finds a field in class hierarchy
    private fun findField(clazz: Class<*>, name: String): Field? {
        var curr: Class<*>? = clazz
        while (curr != null) {
            try { return curr.getDeclaredField(name).apply { isAccessible = true } }
            catch (_: NoSuchFieldException) { curr = curr.superclass }
        }
        return null
    }
}
