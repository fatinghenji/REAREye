package hk.uwu.reareye.hook.core

/**
 * 现有模块迁移门面。
 *
 * 该名称仅用于把业务模块的改动限制在 import 边界；类型实现已经是项目内 HookModule，
 * 后续可按模块逐步把声明名直接替换为 HookModule。
 */
typealias YukiBaseHooker = HookModule
