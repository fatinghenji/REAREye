@file:OptIn(DexKitExperimentalApi::class)

package hk.uwu.reareye.hook.utils

import com.highcapable.kavaref.KavaRef.Companion.asResolver
import org.luckypray.dexkit.DexKitCacheBridge
import org.luckypray.dexkit.annotations.DexKitExperimentalApi

/** Resolves the host's app registry snapshot and its package-to-business map. */
internal class SmartAssistantRegistry(
    private val bridge: DexKitCacheBridge.RecyclableBridge,
    private val classResolver: (String) -> Class<*>,
) {
    val snapshotMethod: DexKitMethodInjectionPoint by lazy {
        requireNotNull(
            resolveDexKitMethodInjectionPoint(
                bridge = bridge,
                cacheKey = "SSC_APP_REGISTRY_SNAPSHOT_METHOD_V3",
            ) {
                findMethod {
                    matcher {
                        paramCount(0)
                        usingStrings("registry loaded: %d apps, %d groups, %d app card intents")
                    }
                }.singleOrNull()
            }) { "DexKit failed to resolve app registry snapshot method" }
    }

    private val snapshotClassName: String by lazy {
        val point = snapshotMethod
        classResolver(point.className).declaredMethods.single {
            it.name == point.methodName && it.parameterTypes.isEmpty()
        }.returnType.name
    }

    private val primaryMapFieldName: String by lazy {
        requireNotNull(
            resolveDexKitFieldValue(
                bridge = bridge,
                cacheKey = "SSC_APP_REGISTRY_PRIMARY_MAP_FIELD_V3",
            ) {
                findField {
                    matcher {
                        declaredClass = snapshotClassName
                        type = "java.lang.Object"
                        readMethods {
                            add {
                                paramTypes(String::class.java)
                                returnType = "boolean"
                                usingStrings("unified.music", "music")
                            }
                        }
                    }
                }.singleOrNull()
            }) { "DexKit failed to resolve app registry primary map field" }
    }

    fun snapshot(): Any {
        val point = snapshotMethod
        val method = classResolver(point.className).declaredMethods.single {
            it.name == point.methodName && it.parameterTypes.isEmpty()
        }.apply { isAccessible = true }
        return requireNotNull(method.invoke(null)) { "App registry snapshot is null" }
    }

    fun primaryMap(snapshot: Any): Any {
        require(snapshot.javaClass.name == snapshotClassName) {
            "Unexpected app registry snapshot class: ${snapshot.javaClass.name}"
        }
        return requireNotNull(snapshot.asResolver().firstField {
            name = primaryMapFieldName
        }.get<Any>()) { "App registry primary map is null" }
    }
}
