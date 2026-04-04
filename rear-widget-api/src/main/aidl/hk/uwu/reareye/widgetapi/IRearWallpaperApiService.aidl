package hk.uwu.reareye.widgetapi;

import android.os.Bundle;

interface IRearWallpaperApiService {
    Bundle getCatalog();

    byte[] getPreview(int wallpaperId);

    boolean switchWallpaper(int wallpaperId);

    boolean syncSchedule(boolean enabled, String scheduleData);
}
