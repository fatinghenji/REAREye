#pragma once

// LSPosed Native Hook 协议定义
// 参考: https://github.com/LSPosed/LSPosed/wiki/Native-Hook
//
// LSPosed 依据模块 assets/native_init 中声明的库名自动加载对应 so，
// 并调用其导出的 native_init(const NativeAPIEntries*)。HyperOS Runtime
// 应用进程没有 ART，整个流程不经过任何 Java 代码。

// if success, return 0
typedef int (*HookFunType)(void *func, void *replace, void **backup);

// if success, return 0
typedef int (*UnhookFunType)(void *func);

typedef void (*NativeOnModuleLoaded)(const char *name, void *handle);

typedef struct {
    uint32_t version;
    HookFunType hook_func;
    UnhookFunType unhook_func;
} NativeAPIEntries;
