package dev.homesev.sounddrive;

import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

/** Keeps the process alive while sound plays with the screen off or in the background. */
public final class PlaybackService extends Service {
    static final String ACTION_STOP = "dev.homesev.sounddrive.STOP";

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        Player p = Player.get(this);
        if (Build.VERSION.SDK_INT >= 29) {
            // Include the location type only when location is permitted, so unlock checks keep
            // working with the screen off; otherwise media playback only.
            int type = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK;
            if (p.locationPermitted()) type |= ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION;
            try {
                startForeground(Player.NOTIFICATION_ID, p.buildNotification(), type);
            } catch (RuntimeException e) {
                startForeground(Player.NOTIFICATION_ID, p.buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
            }
        } else {
            startForeground(Player.NOTIFICATION_ID, p.buildNotification());
        }
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            p.stop(); // also stops this service
            return START_NOT_STICKY;
        }
        if (!p.wantPlay()) stopSelf();
        return START_NOT_STICKY;
    }

    @Override public void onDestroy() {
        stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
