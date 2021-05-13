package com.sbu.demo;

import android.app.Dialog;
import android.content.Context;
import android.widget.TextView;

import com.sbu.demo.R;


public class LoadingDialog extends Dialog {
    private LoadingView loadView;
    private TextView msg;

    public LoadingDialog(Context context) {
        super(context, R.style.progress_dialog);
        this.setContentView(R.layout.loadingdialog);
        this.setCancelable(false);
        this.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        this.loadView = (LoadingView) findViewById(R.id.img_loading);
        this.msg = (TextView) findViewById(R.id.id_tv_loadingmsg);
    }

    public void show(String msg) {
        this.loadView.startLoad();
        this.msg.setText(msg);
        this.show();
    }

    @Override
    public void dismiss() {
        this.loadView.stopLoad();
        super.dismiss();
    }

    private int intTag;
    private String stringTag;

    public void setIntTag(int tag) {
        this.intTag = tag;
    }

    public int getIntTag() {
        return this.intTag;
    }

    public void setStringTag(String tag) {
        this.stringTag = tag;
    }

    public String getStringTag() {
        return this.stringTag;
    }
}
