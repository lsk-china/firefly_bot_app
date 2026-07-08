package com.lsk.android.fireflyai.task;

import android.app.Activity;

import com.lsk.android.fireflyai.helper.WebsocketHelper;
import com.unity3d.player.UnityPlayer;

public class ChangeEmotionTask {
    private static final String TAG = "ChangeEmotionTask";
    private static final String ENDPOINT = "/emotion";
    private final Activity owner;
    private final WebsocketHelper websocketHelper;

    public ChangeEmotionTask(Activity owner, String baseURL) {
        this.owner = owner;
        this.websocketHelper = new WebsocketHelper(baseURL + ENDPOINT);
    }

    public void start() {
        this.websocketHelper.setTextHandler(cmd -> {
            owner.runOnUiThread(() -> UnityPlayer.UnitySendMessage("MyCharacter", "SetEmotion", cmd));
        });
        this.websocketHelper.start();
    }

    public void stop() {
        this.websocketHelper.stop();
    }
}
