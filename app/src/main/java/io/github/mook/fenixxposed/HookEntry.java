package io.github.mook.fenixxposed;

import static de.robv.android.xposed.XposedHelpers.findAndHookConstructor;
import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.findClassIfExists;
import static de.robv.android.xposed.XposedHelpers.findFieldIfExists;
import static de.robv.android.xposed.XposedHelpers.setBooleanField;

import android.content.Context;

import java.util.Objects;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class HookEntry implements IXposedHookLoadPackage {
    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!Objects.equals(lpparam.packageName, "org.mozilla.fennec_fdroid")) {
            return;
        }

        // Disable highlighting for What's New
        findAndHookMethod(
                "org.mozilla.fenix.whatsnew.SharedPreferenceWhatsNewStorage",
                lpparam.classLoader,
                "getDaysSinceUpdate",
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        param.setResult(365);
                    }
                });

        // Disable adding sites to home screen / install webapp
        Class<?> defaultToolbarMenu = findClassIfExists(
                "org.mozilla.fenix.components.toolbar.DefaultToolbarMenu",
                lpparam.classLoader);
        if (defaultToolbarMenu != null) {
            if (findFieldIfExists(defaultToolbarMenu, "isPinningSupported") != null) {
                // Disable highlighting for Install Web App
                findAndHookConstructor(
                        defaultToolbarMenu,
                        Context.class,
                        "mozilla.components.browser.state.store.BrowserStore",
                        boolean.class,
                        "kotlin.jvm.functions.Function1",
                        "androidx.lifecycle.LifecycleOwner",
                        "mozilla.components.concept.storage.BookmarksStorage",
                        "mozilla.components.feature.top.sites.PinnedSiteStorage",
                        boolean.class,
                        new XC_MethodHook() {
                            @Override
                            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                                setBooleanField(param.thisObject, "isPinningSupported", false);
                            }
                        });
            } else {
                XposedBridge.log("FenixXposed: Could not find DefaultToolbarMenu::isPinningSupported");
            }
        } else {
            XposedBridge.log("FenixXposed: Could not find DefaultToolbarMenu");
        }
    }
}
