package io.github.hohojia886.pixelwifidatahelper.hooks

import android.util.Log
import io.github.libxposed.api.XposedModule

object DataHook {
    private const val TARGET_CLASS = "com.android.systemui.qs.tiles.impl.cell.domain.interactor.MobileDataTileUserActionInteractor\$handleSecondaryClick$2"

    fun hook(module: XposedModule, classLoader: ClassLoader) {
        try {
            val lambdaClass = classLoader.loadClass(TARGET_CLASS)
            val invokeSuspendMethod = lambdaClass.getDeclaredMethod("invokeSuspend", Any::class.java)

            module.hook(invokeSuspendMethod).intercept { chain ->
                val lambdaInstance = chain.thisObject ?: return@intercept chain.proceed()
                
                try {
                    // Get 'this$0' which is MobileDataTileUserActionInteractor
                    val interactorField = lambdaInstance.javaClass.getDeclaredField("this$0")
                    interactorField.isAccessible = true
                    val interactor = interactorField.get(lambdaInstance) ?: return@intercept chain.proceed()

                    // Get mobileConnectionsRepository from interactor
                    val repoField = interactor.javaClass.getDeclaredField("mobileConnectionsRepository")
                    repoField.isAccessible = true
                    val repo = repoField.get(interactor) ?: return@intercept chain.proceed()

                    // 1. Get default data sub ID: repo.getDefaultDataSubId().getValue()
                    val getDefaultDataSubIdMethod = repo.javaClass.getMethod("getDefaultDataSubId")
                    val subIdFlow = getDefaultDataSubIdMethod.invoke(repo)
                    
                    val getValueMethod = subIdFlow.javaClass.getMethod("getValue")
                    val subId = getValueMethod.invoke(subIdFlow) as? Int

                    if (subId != null) {
                        // 2. Get connection repo for sub ID: repo.getRepoForSubId(subId)
                        val getRepoForSubIdMethod = repo.javaClass.getMethod("getRepoForSubId", Int::class.javaPrimitiveType)
                        val connectionRepo = getRepoForSubIdMethod.invoke(repo, subId)

                        if (connectionRepo != null) {
                            Log.i("PixelDataFix", "Bypassing confirmation dialog, enabling mobile data directly (SubID: $subId)")
                            // 3. Enable data: connectionRepo.setDataEnabled(true)
                            val setDataEnabledMethod = connectionRepo.javaClass.getMethod("setDataEnabled", Boolean::class.javaPrimitiveType)
                            setDataEnabledMethod.invoke(connectionRepo, true)
                        } else {
                            Log.e("PixelDataFix", "Could not get ConnectionRepository for subId $subId")
                        }
                    } else {
                        Log.e("PixelDataFix", "Default Data SubID is null")
                    }
                } catch (e: Exception) {
                    Log.e("PixelDataFix", "Error in MobileData hook", e)
                    return@intercept chain.proceed()
                }
                
                // Return kotlin.Unit to skip original dialog showing logic
                Unit
            }
            Log.i("PixelDataFix", "Successfully hooked MobileData confirmation dialog bypass")
        } catch (e: Throwable) {
            Log.e("PixelDataFix", "Failed to hook MobileData bypass", e)
        }
    }
}
