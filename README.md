# REAREye
- 针对小米17Pro/Pro Max的背屏增强模块


## 功能
- 允许自定义应用在背屏中开启
> 使用ADB或者其他应用开启 目前没有计划内置启动器
- 允许自定义的音乐控件在背屏中显示
> 可以支持任意使用媒体控件的APP
> 
> 如: Apple Music, BiliBili等
>
> **修改白名单应用需要重启 com.xiaomi.subscreencenter - 背屏**

- 将指定的应用设为后台白名单（防止在背屏被系统杀除）

> 修改后需要重启系统生效

- 强制更新音乐控件状态

> 部分国外音乐APP在背屏息屏状态时播放下一首歌曲（自动切换）
>
> 可能不会在背屏上立刻变更显示内容（在时钟变化时系统会触发更新）
>
> 在开启此功能后 音乐播放状态发生变更时会强制触发更新

## 杂项功能

因为懒得重新写一个独立的Xposed模块

所以先写在这了

- 移除国行GMS服务限制

> 启用并重启系统后将会强制在SystemConfig中移除下列feature
>
> > cn.google.services
> >
> > com.google.android.feature.services_updater
>
> 开启该功能后可以解锁GMS针对国行系统的限制
>
> 例如 `Google Location History`, `Google Map Timeline` 等功能
>
> 理论上可以彻底替代 https://github.com/fei-ke/unlock-cn-gms
> 或类似的 Magisk/KernelSU 模块
> 并且可以提供更广泛的兼容性