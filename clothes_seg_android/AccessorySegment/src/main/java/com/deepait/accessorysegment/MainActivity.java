package com.deepait.accessorysegment;


import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import com.deepait.accessorysegment.tnn.R;

public class MainActivity extends Activity {

    private TextView lightLiveCheckBtn;

    private boolean isShowedActivity = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
//       Debug.waitForDebugger();

        init();

    }

    private void init() {

        findViewById(R.id.body_detect_btn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isShowedActivity) {
                    isShowedActivity = true;
                    Intent intent = new Intent();
                    Activity activity = MainActivity.this;
                    intent.setClass(activity, AccessorySegmentActivity.class);
                    activity.startActivity(intent);
                }
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        isShowedActivity = false;
    }

}
