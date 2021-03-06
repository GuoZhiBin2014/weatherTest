package com.gzb.coolweather.task;

import android.os.Handler;

import com.gzb.coolweather.activity.NewsFragment;

import java.util.TimerTask;


/**
 * Created by huangyaling on 18-1-29.
 */

public class BannerTimerTask extends TimerTask {
    Handler handler;

    public BannerTimerTask(Handler handler) {
        super();
        this.handler = handler;
    }

    @Override
    public void run() {
        handler.sendEmptyMessage(NewsFragment.AUTOBANNER_CODE);
    }
}
