package com.deepait.bodysegment;

import android.app.Application;
import android.content.res.Configuration;
import android.support.annotation.NonNull;

/**
 * @author: yrs
 * @date: 1/4/21 2:49 PM
 * @desc:
 */
public class LocalApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();

//        if (LeakCanary.isInAnalyzerProcess(this)) {
//            // This process is dedicated to LeakCanary for
//            // heap analysis.
//            // You should not init your app in this process.
//            return;
//        }
//
//        LeakCanary.install(this);

    }
}
