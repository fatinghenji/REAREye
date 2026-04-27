package hk.uwu.reareye.widgetapi;

import android.os.Bundle;
import android.os.ParcelFileDescriptor;

interface IRearWallpaperApiService {
    Bundle getCatalog();

    byte[] getPreview(int wallpaperId);

    boolean switchWallpaper(int wallpaperId);

    boolean syncSchedule(boolean enabled, String scheduleData);

    Bundle importWallpaperPackage(
        in ParcelFileDescriptor packageFd,
        String displayNameHint,
        String previewUri,
        in Bundle options
    );

    Bundle updateWallpaperMetadata(int wallpaperId, String previewUri, in Bundle options);

    Bundle generateWallpaperPreview(int wallpaperId);

    Bundle deleteWallpaper(int wallpaperId);

    Bundle resolveTemplateConfigState(int wallpaperId, String currentOneConfigJson);

    Bundle saveTemplateConfig(int wallpaperId, String oneConfigJson);
}
