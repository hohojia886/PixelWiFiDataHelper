package io.github.hohojia886.pixelwifidatahelper.hooks

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.util.Log
import io.github.libxposed.api.XposedModule

object DataHook {
    private const val TARGET_CLASS = "com.android.systemui.qs.tiles.impl.cell.domain.interactor.MobileDataTileUserActionInteractor\$handleSecondaryClick$2"
    @Volatile private var isEnabled = true
    private var isInitialized = false

    private fun updateState(context: Context) {
        Log.d("PixelDataFix", "Attempting to load state from provider... context: ${context.packageName}")
        runCatching {
            val uri = Uri.parse("content://io.github.hohojia886.pixelwifidatahelper.prefs")
            val bundle = context.contentResolver.call(uri, "get", null, null)
            if (bundle != null) {
                isEnabled = bundle.getBoolean("data_fix", true)
                isInitialized = true
                Log.i("PixelDataFix", "Status loaded from provider: $isEnabled (Total keys: ${bundle.size()})")
            } else {
                Log.e("PixelDataFix", "Failed to load status: bundle is null. Is the provider exported and authority correct?")
            }
        }.onFailure {
            Log.e("PixelDataFix", "Critical error loading status from provider: ${it.message}", it)
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
                                if (intent != null && intent.hasExtra("data_fix")) {
                                    isEnabled = intent.getBooleanExtra("data_fix", true)
                                    isInitialized = true
                                    Log.i("PixelDataFix", "Data fix status updated by broadcast: $isEnabled")
                                }
                            }
                        }, filter, "io.github.hohojia886.pixelwifidatahelper.PERMISSION", null, Context.RECEIVER_EXPORTED)
                    }
                    chain.proceed()
                }
            }

            val lambdaClass = classLoader.loadClass(TARGET_CLASS)
            val invokeSuspendMethod = lambdaClass.getDeclaredMethod("invokeSuspend", Any::class.java)

            module.hook(invokeSuspendMethod).intercept { chain ->
                if (!isEnabled) {
                    return@intercept chain.proceed()
                }

                val lambdaInstance = chain.thisObject ?: return@intercept chain.proceed()
                
                try {
                    val interactorField = lambdaInstance.javaClass.getDeclaredField("this$0")
                    interactorField.isAccessible = true
                    val interactor = interactorField.get(lambdaInstance) ?: return@intercept chain.proceed()

                    val repoField = interactor.javaClass.getDeclaredField("mobileConnectionsRepository")
                    repoField.isAccessible = true
                    val repo = repoField.get(interactor) ?: return@intercept chain.proceed()

                    val getDefaultDataSubIdMethod = repo.javaClass.getMethod("getDefaultDataSubId")
                    val subIdFlow = getDefaultDataSubIdMethod.invoke(repo)
                    
                    val getValueMethod = subIdFlow.javaClass.getMethod("getValue")
                    val subId = getValueMethod.invoke(subIdFlow) as? Int

                    if (subId != null) {
                        val getRepoForSubIdMethod = repo.javaClass.getMethod("getRepoForSubId", Int::class.javaPrimitiveType)
                        val connectionRepo = getRepoForSubIdMethod.invoke(repo, subId)

                        if (connectionRepo != null) {
                            Log.i("PixelDataFix", "Data Fix Active: Enabling mobile data directly (SubID: $subId)")
                            val setDataEnabledMethod = connectionRepo.javaClass.getMethod("setDataEnabled", Boolean::class.javaPrimitiveType)
                            setDataEnabledMethod.invoke(connectionRepo, true)
                            return@intercept Unit
                        }
                    }
                } catch (e: Exception) {
                    Log.e("PixelDataFix", "Error in MobileData hook", e)
                }
                
                chain.proceed()
            }
        } catch (e: Throwable) {
            Log.e("PixelDataFix", "Failed to hook MobileData bypass", e)
        }
    }
}
