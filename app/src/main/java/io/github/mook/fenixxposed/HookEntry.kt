package io.github.mook.fenixxposed

import android.content.Context
import android.content.res.XModuleResources
import android.util.Log
import android.view.View
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge.hookAllConstructors
import de.robv.android.xposed.XposedHelpers.*
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import java.lang.reflect.Proxy
import java.util.*

class HookEntry : IXposedHookLoadPackage, IXposedHookZygoteInit {
    companion object {
        const val TAG = "FenixXposed"
    }

    private var packageNames = arrayOf<String>()

    override fun initZygote(startupParam: IXposedHookZygoteInit.StartupParam?) {
        startupParam ?: return
        val resources = XModuleResources.createInstance(startupParam.modulePath, null)
        packageNames = resources.getStringArray(R.array.xposedscope)
    }

    override fun handleLoadPackage(lpparam: LoadPackageParam) {
        packageNames.contains(lpparam.packageName) || return

        // Disable highlighting for What's New
        findAndHookMethod(
            "mozilla.components.browser.menu.item.BrowserMenuHighlightableItem",
            lpparam.classLoader,
            "updateHighlight",
            View::class.java,
            Boolean::class.java,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    param.args[1] = false
                }
            })

        // Disable adding sites to home screen / install webapp
        val defaultToolbarMenu = findClassIfExists(
            "org.mozilla.fenix.components.toolbar.DefaultToolbarMenu",
            lpparam.classLoader
        )
        if (defaultToolbarMenu != null) {
            if (findFieldIfExists(defaultToolbarMenu, "isPinningSupported") != null) {
                // Disable highlighting for Install Web App
                hookAllConstructors(defaultToolbarMenu,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            setBooleanField(
                                param.thisObject,
                                "isPinningSupported",
                                false
                            )
                        }
                    })
            } else {
                Log.e(TAG, "Could not find DefaultToolbarMenu::isPinningSupported")
            }
        } else {
            Log.e(TAG, "Could not find DefaultToolbarMenu")
        }

        // Disable the main menu Sync UI
        val classBrowserMenuSignIn = findClassIfExists(
            "org.mozilla.fenix.components.toolbar.BrowserMenuSignIn",
            lpparam.classLoader
        )
        if (classBrowserMenuSignIn != null) {
             hookAllConstructors(classBrowserMenuSignIn,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam?) {
                        param?.thisObject ?: return
                        try {
                            val iface = findClass(
                                "kotlin.jvm.functions.Function0",
                                lpparam.classLoader
                            )
                            val lambda = Proxy.newProxyInstance(
                                lpparam.classLoader,
                                arrayOf(iface)
                            ) { _, _, _ -> false }
                            setObjectField(param.thisObject, "visible", lambda)
                        } catch (ex: Throwable) {
                            Log.d(TAG, "Failed to hide BrowserMenuSignIn:", ex)
                        }
                    }
                }
            )
        } else {
            Log.d(TAG, "Failed to locate BrowserMenuSignIn")
        }

        // Hide synced tabs
        findAndHookMethod(
            "org.mozilla.fenix.tabstray.TabLayoutMediator",
            lpparam.classLoader,
            "start",
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam?) {
                    param?.thisObject ?: return
                    val layout = getObjectField(param.thisObject, "tabLayout") ?: return
                    // removeTab has been optimized out, we need to get at the tab list directly
                    val tabs = getObjectField(layout, "tabs") as ArrayList<*>
                    val viewPool = getObjectField(layout, "tabViewPool")
                    for (i in (0 until tabs.size).reversed()) {
                        val tab = tabs[i]
                        val contentDesc = getObjectField(tab, "contentDesc")
                        Objects.equals(contentDesc, "Synced tabs") || continue
                        try {
                            tabs.removeAt(i)
                            val slidingTabIndicator =
                                getObjectField(layout, "slidingTabIndicator") ?: continue
                            val view = callMethod(
                                slidingTabIndicator,
                                "getChildAt",
                                i
                            )
                            callMethod(slidingTabIndicator, "removeViewAt", i)
                            view ?: continue
                            findMethodExactIfExists(view.javaClass, "reset")?.invoke(view)
                            viewPool ?: continue
                            findMethodExactIfExists(
                                viewPool.javaClass,
                                "release",
                                view.javaClass
                            )?.invoke(viewPool, view)
                            Log.d(TAG, "Removed tab tray tab: $contentDesc")
                        } catch (ex: Throwable) {
                            Log.e(TAG, "Failed to remove tab #$i:", ex)
                        }
                    }
                }
            }
        )
    }
}