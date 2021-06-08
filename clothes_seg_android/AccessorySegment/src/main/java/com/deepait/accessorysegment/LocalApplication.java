package com.deepait.accessorysegment;

import android.app.Application;

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
