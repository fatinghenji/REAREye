package hk.uwu.reareye.hook.scopes.thememanager.modules

import com.highcapable.kavaref.KavaRef.Companion.asResolver
import com.highcapable.kavaref.KavaRef.Companion.resolve
import hk.uwu.reareye.hook.core.YLog
import hk.uwu.reareye.hook.core.YukiBaseHooker

/**
 * Change only the device query parameter for the AI generated rear-screen page.
 *
 * The request is issued by ThemeManager even though the feature is owned by the
 * SubScreenCenter integration. Hooking ParamInterceptor's request builder keeps the change at
 * the final parameter map and leaves Build.DEVICE and all other requests alone.
 */
class AiGeneratedAppDeviceHook : YukiBaseHooker() {
    companion object {
        private const val TAG = "REAREye-AiGeneratedAppDevice"
        private const val PARAM_DEVICE = "device"
        private const val PARAM_VERSION = "version"
        private const val DEVICE_VALUE = "madrid"
        private const val ENDPOINT = "/native/page/v3/AI_GENERATED_APP"
        private const val CACHE_CONTROL_HEADER = "Cache-Control"
        private const val CACHE_CONTROL_VALUE = "no-cache, no-store"
    }

    override fun onHook() {
        loadApp("com.android.thememanager") {
            val requestClass = "okhttp3.Request".toClass()
            val requestBuilderClass = "okhttp3.Request\$Builder".toClass()
            val httpUrlClass = "okhttp3.HttpUrl".toClass()
            val headersClass = "okhttp3.Headers".toClass()
            val requestBodyClass = "okhttp3.RequestBody".toClass()
            val responseClass = "okhttp3.Response".toClass()
            val responseBodyClass = "okhttp3.ResponseBody".toClass()
            val chainClass = "okhttp3.Interceptor\$Chain".toClass()
            val interceptorClass =
                "com.android.thememanager.basemodule.network.theme.interceptors.ParamInterceptor"
                    .toClass()

            interceptorClass.resolve().firstMethod {
                // Match ParamInterceptor's request builder by signature. Its
                // method name is obfuscated and must not be relied upon.
                parameters(requestClass, LinkedHashMap::class.java, String::class.java)
                returnType = requestClass
            }.hook {
                before {
                    val request = args[0] ?: return@before
                    if (!isAiGeneratedAppRequest(request, httpUrlClass)) return@before

                    @Suppress("UNCHECKED_CAST")
                    val params = args[1] as? MutableMap<Any?, Any?> ?: return@before
                    val previous = params[PARAM_DEVICE]
                    val previousVer = params[PARAM_VERSION] as String
                    params[PARAM_DEVICE] = DEVICE_VALUE
                    params[PARAM_VERSION] = previousVer.split(".").toMutableList().apply {
                        this[2] = "499"
                    }.joinToString(".")
                    YLog.debug("[$TAG] device=$previous -> $DEVICE_VALUE version=$previousVer -> ${params[PARAM_VERSION]}")
                }
                after {
                    val request = result ?: return@after
                    if (!isAiGeneratedAppRequest(request, httpUrlClass)) return@after
                    val uncachedRequest = addNoCacheHeaders(
                        request,
                        requestClass,
                        requestBuilderClass,
                    ) ?: return@after
                    result = uncachedRequest
                    YLog.debug("[$TAG] forced fresh response for $ENDPOINT")
                }
            }

            @Suppress("ConstantConditionIf")
            if (false) {
                interceptorClass.resolve().firstMethod {
                    parameters(chainClass)
                    returnType = responseClass
                }.hook {
                    after {
                        val request = chainRequest(args[0], requestClass) ?: return@after
                        if (isAiGeneratedAppRequest(request, httpUrlClass)) {
                            debugResponse(
                                result,
                                headersClass,
                                responseBodyClass,
                                "response.after-param",
                            )
                        }
                    }
                }

                val curlClass =
                    "com.android.thememanager.basemodule.network.theme.CurlLoggingInterceptor"
                        .toClass()
                curlClass.resolve().firstMethod {
                    // CurlLoggingInterceptor(Interceptor.Chain) -> Response.
                    // Match by signature because the method name is obfuscated.
                    parameters(chainClass)
                    returnType = responseClass
                }.hook {
                    before {
                        val request = chainRequest(args[0], requestClass) ?: return@before
                        if (isAiGeneratedAppRequest(request, httpUrlClass)) {
                            debugRequest(request, httpUrlClass, headersClass, requestBodyClass)
                        }
                    }
                }

                YLog.debug("[$TAG] installed for $ENDPOINT with request/response debug")
            }
        }
    }

    private fun chainRequest(chain: Any?, requestClass: Class<*>): Any? {
        if (chain == null) return null
        return runCatching {
            chain.asResolver().firstMethod {
                parameterCount = 0
                returnType = requestClass
            }.invoke()
        }.getOrNull()
    }

    private fun addNoCacheHeaders(
        request: Any,
        requestClass: Class<*>,
        requestBuilderClass: Class<*>,
    ): Any? {
        return runCatching {
            val builder = request.asResolver().firstMethod {
                parameterCount = 0
                returnType = requestBuilderClass
            }.invoke() ?: return@runCatching null
            builder.asResolver().firstMethod {
                parameters(String::class.java, String::class.java)
                returnType = requestBuilderClass
            }.invoke(CACHE_CONTROL_HEADER, CACHE_CONTROL_VALUE)
            builder.asResolver().firstMethod {
                parameterCount = 0
                returnType = requestClass
            }.invoke()
        }.onFailure {
            YLog.debug("[$TAG] unable to add no-cache headers: $it")
        }.getOrNull()
    }

    private fun requestUrl(request: Any, httpUrlClass: Class<*>): String? {
        val url = runCatching {
            request.asResolver().firstMethod {
                parameterCount = 0
                returnType = httpUrlClass
            }.invoke()?.toString()
        }.getOrNull() ?: return null
        return url
    }

    private fun isAiGeneratedAppRequest(request: Any, httpUrlClass: Class<*>): Boolean {
        val url = requestUrl(request, httpUrlClass) ?: return false
        val requestPath = url.substringBefore('?').substringBefore('#')
        return requestPath.endsWith(ENDPOINT)
    }

    private fun debugRequest(
        request: Any,
        httpUrlClass: Class<*>,
        headersClass: Class<*>,
        requestBodyClass: Class<*>,
    ) {
        debug("request", request.toString())
        debug("request.url", requestUrl(request, httpUrlClass).orEmpty())
        runCatching {
            request.asResolver().firstMethod {
                parameterCount = 0
                returnType = headersClass
            }.invoke()?.let { debug("request.headers", it.toString()) }
        }.onFailure { YLog.debug("[$TAG] request.headers unavailable: $it") }
        runCatching {
            request.asResolver().firstMethod {
                parameterCount = 0
                returnType = requestBodyClass
            }.invoke()?.let { debug("request.body", it.toString()) }
                ?: debug("request.body", "<none>")
        }.onFailure { YLog.debug("[$TAG] request.body unavailable: $it") }
    }

    private fun debugResponse(
        response: Any?,
        headersClass: Class<*>,
        responseBodyClass: Class<*>,
        label: String,
    ) {
        if (response == null) {
            debug(label, "<null>")
            return
        }
        debug(label, response.toString())
        runCatching {
            response.asResolver().firstMethod {
                parameterCount = 0
                returnType = headersClass
            }.invoke()?.let { debug("$label.headers", it.toString()) }
        }.onFailure { YLog.debug("[$TAG] $label.headers unavailable: $it") }
        runCatching {
            val peekBody = response.asResolver().firstMethod {
                parameters(Long::class.javaPrimitiveType!!)
                returnType = responseBodyClass
            }.invoke(1_048_576L)
            if (peekBody == null) {
                debug("$label.body", "<none>")
            } else {
                val bodyText = peekBody.asResolver().firstMethod {
                    parameterCount = 0
                    returnType = String::class.java
                    superclass()
                }.invoke()?.toString().orEmpty()
                debug("$label.body", bodyText)
            }
        }.onFailure { YLog.debug("[$TAG] $label.body unavailable: $it") }
    }

    private fun debug(label: String, value: String) {
        val text = value.ifEmpty { "<empty>" }
        val chunks = text.chunked(2000)
        chunks.forEachIndexed { index, chunk ->
            YLog.debug("[$TAG] $label[${index + 1}/${chunks.size}] $chunk")
        }
    }
}
