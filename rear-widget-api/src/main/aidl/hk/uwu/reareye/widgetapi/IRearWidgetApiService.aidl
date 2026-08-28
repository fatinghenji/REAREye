package hk.uwu.reareye.widgetapi;

import android.os.Bundle;

interface IRearWidgetApiService {
    void registerBusinessFile(String business, String filePath);

    void unregisterBusinessFile(String business);

    void registerBusiness(
            String targetPackage,
            String business,
            String filePath,
            int defaultIndex,
            int defaultPriority
    );

    void registerBusinessWithoutFile(
            String targetPackage,
            String business,
            int defaultIndex,
            int defaultPriority
    );

    void unregisterBusiness(String targetPackage, String business);

    void registerSceneRoute(String targetPackage, String scene, String business);

    void unregisterSceneRoute(String targetPackage, String scene);

    void disableBusinessDisplay(String targetPackage, String business);

    void disableCardDisplay(String targetPackage, String business, String cardId);

    void postNotice(String targetPackage, String business, in Bundle payload, in Bundle options);

    void updateNotice(
            in Bundle ticket,
            in Bundle payload,
            in Bundle options,
            boolean updatePayload,
            boolean updateOptions
    );

    void removeNotice(in Bundle ticket);

    void syncState();

    Bundle resolveTemplateImagePreview(String business, String sourceFilePath, String imageValue);

    Bundle resolveTemplateConfigState(String business, String sourceFilePath, String currentOneConfigJson);

    String importCardCustomImage(String cardKey, String fieldName, String sourceUri, String displayNameHint);
}
