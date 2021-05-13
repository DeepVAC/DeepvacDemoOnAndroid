package com.sbu.demo;

import android.app.Application;

import com.zxy.recovery.core.Recovery;

public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();

        Recovery.getInstance()
                .debug(true)
                .recoverInBackground(false)
                .recoverStack(true)
                .mainPage(MainActivity.class)
                .recoverEnabled(true)
//                .callback(new MyCrashCallback())
                .silent(false, Recovery.SilentMode.RECOVER_ACTIVITY_STACK)
//                .skip(TestActivity.class)
                .init(this);
    }
}
