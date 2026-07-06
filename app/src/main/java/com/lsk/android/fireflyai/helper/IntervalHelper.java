package com.lsk.android.fireflyai.helper;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

public class IntervalHelper {
    private static final Handler handler = new Handler(Looper.getMainLooper());
    private static final List<Runnable> runnables = new ArrayList<>();
    private static final String TAG = "IntervalHelper";

    public static void pause() {
        for (Runnable task : runnables) {
            handler.removeCallbacks(task);
        }
    }

    public static void resume() {
        for (Runnable task: runnables) {
            // wait 0.5s for camera initialization
            handler.postDelayed(task, 500);
        }
    }
    public static void setInterval(Runnable task, int interval) {
        Runnable r = new Runnable() {
            @Override
            public void run() {
                try {
                    task.run();
                } catch (Exception e) {
                    Log.e(TAG, "run: caught exception in task", e);
                } finally {
                    handler.postDelayed(this, interval);
                }
            }
        };
        runnables.add(r);
        handler.post(r);
    }
}
