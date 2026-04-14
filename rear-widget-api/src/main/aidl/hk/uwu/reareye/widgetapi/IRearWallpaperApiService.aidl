package hk.uwu.reareye.widgetapi;

import android.os.Bundle;

interface IRearWallpaperApiService {
    Bundle getCatalog();

    byte[] getPreview(int wallpaperId);

    boolean switchWallpaper(int wallpaperId);

    boolean syncSchedule(boolean enabled, String scheduleData);

    Bundle importWallpaperPackage(
        String packageUri,
        String displayNameHint,
        String metadataUri,
        String previewUri,
        in Bundle options
    );

    Bundle updateWallpaperMetadata(int wallpaperId, String previewUri, in Bundle options);

    Bundle generateWallpaperPreview(int wallpaperId);

    Bundle deleteWallpaper(int wallpaperId);
}
