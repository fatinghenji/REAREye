# REAR Widget API

- 由REAREye提供的背屏组件管理API
- 通过此API您可以在您的应用/模块中操作小米妙想背屏的组件

## 协议

- REAR Widget API部分单独使用LGPLv3协议开源

## 依赖

```kotlin
repositories {
    maven("https://repo.fastmcmirror.org/content/repositories/releases/")
}

dependencies {
    implementation("hk.uwu.reareye:rear-widget-api:1.0.1")
}
```

## 注册权限

```xml

<uses-permission android:name="hk.uwu.reareye.permission.ACCESS_REAR_WIDGET_API" />
```