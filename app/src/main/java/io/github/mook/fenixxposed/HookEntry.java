package io.github.mook.fenixxposed;

import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;

import java.util.Objects;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class HookEntry implements IXposedHookLoadPackage {
    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!Objects.equals(lpparam.packageName, "org.mozilla.fennec_fdroid")) {
            return;
        }

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
    }
}
