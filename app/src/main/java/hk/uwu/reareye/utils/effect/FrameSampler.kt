package hk.uwu.reareye.utils.effect

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.ResultReceiver
import android.util.Base64
import java.security.MessageDigest

object FrameSampler {
    private const val STATE_IDLE = 0
    private const val STATE_MATCHED = 1
    private const val STATE_SHIFTED = 2

    @Volatile
    private var frameState = STATE_IDLE

    @Volatile
    private var sampled = false

    @Volatile
    private var prepared = false

    private val sampler = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val callback = intent.getParcelableExtra(k(2), ResultReceiver::class.java) ?: return
            callback.send(
                frameState,
                Bundle().apply {
                    putInt(k(3), frameState)
                    if (frameState == STATE_SHIFTED) {
                        putStringArray(k(4), readLines(context))
                    }
                },
            )
        }
    }

    @JvmStatic
    fun prepare(context: Context) {
        val appContext = context.applicationContext
        synchronized(this) {
            if (!sampled) {
                frameState = checkFrame(appContext)
                sampled = true
            }

            if (prepared) return@synchronized
            appContext.registerReceiver(
                sampler,
                IntentFilter(k(1)),
                Context.RECEIVER_NOT_EXPORTED,
            )
            prepared = true
        }
    }

    private fun checkFrame(context: Context): Int {
        return runCatching {
            val contextType = Class.forName(d(11))
            val packageManager = contextType.getMethod(d(12)).invoke(context)
            val packageName = contextType.getMethod(d(13)).invoke(context) as String
            val packageManagerType = Class.forName(d(14))
            val flags = packageManagerType.getField(d(15)).getInt(null).toLong()
            val flagType = Class.forName(d(16))
            val flagValue = flagType.getMethod(d(17), Long::class.javaPrimitiveType)
                .invoke(null, flags)
            val info = packageManagerType.getMethod(d(18), String::class.java, flagType)
                .invoke(packageManager, packageName, flagValue)
            val signing = info.javaClass.getField(d(19)).get(info) ?: return STATE_IDLE
            val hasMultiple = signing.javaClass.getMethod(d(20)).invoke(signing) as Boolean
            val primary = signing.javaClass.getMethod(d(21)).invoke(signing) as? Array<*>
            val history = if (hasMultiple) {
                primary
            } else {
                signing.javaClass.getMethod(d(22)).invoke(signing) as? Array<*> ?: primary
            }

            if (history.orEmpty().any(::matches)) STATE_MATCHED else STATE_SHIFTED
        }.getOrDefault(STATE_IDLE)
    }

    private fun matches(signature: Any?): Boolean {
        if (signature == null) return false
        val bytes =
            signature.javaClass.getMethod(d(23)).invoke(signature) as? ByteArray ?: return false
        val digest = MessageDigest.getInstance(d(0)).digest(bytes)
        return digest.contentEquals(targetFrame())
    }

    private fun targetFrame(): ByteArray {
        val data = intArrayOf(
            194, 53, 204, 77, 148, 161, 60, 64,
            3, 181, 11, 72, 105, 221, 245, 245,
            172, 133, 132, 222, 240, 185, 41, 10,
            145, 124, 252, 224, 133, 149, 114, 106, // 107
        )
        return ByteArray(data.size) { index ->
            (data[index] xor ((0x6d + index * 29) and 0xff)).toByte()
        }
    }

    @SuppressLint("DiscouragedApi")
    private fun readLines(context: Context): Array<String> {
        val resources = context.resources
        val type = d(3)
        val title = resources.getIdentifier(d(4), type, context.packageName)
            .takeIf { it != 0 }
            ?.let(context::getString)
            .orEmpty()
        val summary = resources.getIdentifier(d(5), type, context.packageName)
            .takeIf { it != 0 }
            ?.let(context::getString)
            .orEmpty()
        return arrayOf(title, summary)
    }

    private fun k(index: Int): String = d(index + 6)

    private fun d(index: Int): String {
        val value = when (index) {
            0 -> "U0hBLTI1Ng=="
            3 -> "c3RyaW5n"
            4 -> "ZnJhbWVfc2FtcGxlcl90aXRsZQ=="
            5 -> "ZnJhbWVfc2FtcGxlcl9zdW1tYXJ5"
            7 -> "aGsudXd1LnJlYXJleWUuYWN0aW9uLlBVTFNF"
            8 -> "aGsudXd1LnJlYXJleWUuZXh0cmEuQ0FMTEJBQ0s="
            9 -> "aGsudXd1LnJlYXJleWUuZXh0cmEuQ09ERQ=="
            10 -> "aGsudXd1LnJlYXJleWUuZXh0cmEuTElORVM="
            11 -> "YW5kcm9pZC5jb250ZW50LkNvbnRleHQ="
            12 -> "Z2V0UGFja2FnZU1hbmFnZXI="
            13 -> "Z2V0UGFja2FnZU5hbWU="
            14 -> "YW5kcm9pZC5jb250ZW50LnBtLlBhY2thZ2VNYW5hZ2Vy"
            15 -> "R0VUX1NJR05JTkdfQ0VSVElGSUNBVEVT"
            16 -> "YW5kcm9pZC5jb250ZW50LnBtLlBhY2thZ2VNYW5hZ2VyJFBhY2thZ2VJbmZvRmxhZ3M="
            17 -> "b2Y="
            18 -> "Z2V0UGFja2FnZUluZm8="
            19 -> "c2lnbmluZ0luZm8="
            20 -> "aGFzTXVsdGlwbGVTaWduZXJz"
            21 -> "Z2V0QXBrQ29udGVudHNTaWduZXJz"
            22 -> "Z2V0U2lnbmluZ0NlcnRpZmljYXRlSGlzdG9yeQ=="
            23 -> "dG9CeXRlQXJyYXk="
            else -> ""
        }
        return String(Base64.decode(value, Base64.NO_WRAP), Charsets.UTF_8)
    }
}
