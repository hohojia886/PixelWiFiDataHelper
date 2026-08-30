package io.github.hohojia886.pixelwifidatahelper.hooks

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.util.Log
import io.github.hohojia886.pixelwifidatahelper.utils.Constants.ACTION_UPDATE
import io.github.hohojia886.pixelwifidatahelper.utils.Constants.KEY_DATA_FIX
import io.github.hohojia886.pixelwifidatahelper.utils.Constants.LOG_TAG
import io.github.hohojia886.pixelwifidatahelper.utils.Constants.PERMISSION_UPDATE
import io.github.hohojia886.pixelwifidatahelper.utils.Constants.PROVIDER_URI
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Field
import java.lang.reflect.Method

object DataHook {
    private const val TARGET_CLASS = "com.android.systemui.qs.tiles.impl.cell.domain.interactor.MobileDataTileUserActionInteractor\$handleSecondaryClick$2"
    @Volatile private var isEnabled = true

    private var interactorField: Field? = null
    private var repoField: Field? = null
    private var getDefaultDataSubIdMethod: Method? = null
    private var getRepoForSubIdMethod: Method? = null

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
        try {
            val lambdaClass = classLoader.loadClass(TARGET_CLASS)
            val invokeSuspendMethod = lambdaClass.getDeclaredMethod("invokeSuspend", Any::class.java)

            runCatching {
                interactorField = lambdaClass.getDeclaredField("this$0").apply { isAccessible = true }
                val interactorClass = interactorField?.type ?: return@runCatching
                repoField = interactorClass.getDeclaredField("mobileConnectionsRepository").apply { isAccessible = true }
                val repoClass = repoField?.type ?: return@runCatching
                getDefaultDataSubIdMethod = repoClass.getMethod("getDefaultDataSubId")
                getRepoForSubIdMethod = repoClass.getMethod("getRepoForSubId", Int::class.javaPrimitiveType)
            }.onFailure {
                Log.e(LOG_TAG, "DataHook: Failed to find internal methods/fields", it)
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
                                if (intent != null && intent.hasExtra(KEY_DATA_FIX)) {
                                    isEnabled = intent.getBooleanExtra(KEY_DATA_FIX, true)
                                    Log.i(LOG_TAG, "DataHook: Data fix status updated by broadcast: $isEnabled")
                                }
                            }
                        }, filter, PERMISSION_UPDATE, null, Context.RECEIVER_EXPORTED)
                    }
                    chain.proceed()
                }
            }

            module.hook(invokeSuspendMethod).intercept { chain ->
                if (!isEnabled) {
                    return@intercept chain.proceed()
                }

                val lambdaInstance = chain.thisObject ?: return@intercept chain.proceed()
                
                val success = runCatching {
                    val interactor = interactorField?.get(lambdaInstance) ?: return@runCatching false
                    val repo = repoField?.get(interactor) ?: return@runCatching false

                    val subIdFlow = getDefaultDataSubIdMethod?.invoke(repo) ?: return@runCatching false
                    val subId = subIdFlow.javaClass.getMethod("getValue").invoke(subIdFlow) as? Int ?: return@runCatching false

                    if (subId != null) {
                        val connectionRepo = getRepoForSubIdMethod?.invoke(repo, subId) ?: return@runCatching false

                        Log.i(LOG_TAG, "DataHook: Enabling mobile data directly (SubID: $subId)")
                        val setDataEnabledMethod = connectionRepo.javaClass.getMethod("setDataEnabled", Boolean::class.javaPrimitiveType)
                        setDataEnabledMethod.invoke(connectionRepo, true)
                        true
                    } else false
                }.getOrElse {
                    Log.e(LOG_TAG, "DataHook: Error in MobileData hook", it)
                    false
                }
                
                if (success) Unit else chain.proceed()
            }
        } catch (e: Throwable) {
            Log.e(LOG_TAG, "DataHook: Failed to hook MobileData bypass", e)
        }
    }
}
