package hk.uwu.reareye.utils

import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader

object RootHelper {
    private const val TAG = "RootHelper"
    fun hasRootAccess(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("su")
            val os = process.outputStream
            os.write("exit\n".toByteArray())
            os.flush()
            val exitCode = process.waitFor()
            exitCode == 0
        } catch (e: Exception) {
            Log.e(TAG, "Root access check failed", e)
            false
        }
    }

    fun executeRootCommand(command: String): Pair<Int, String> {
        return try {
            val process = Runtime.getRuntime().exec("su")
            val os = process.outputStream
            val writer = os.writer()

            writer.write("$command\n")
            writer.write("exit\n")
            writer.flush()
            writer.close()

            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val errorReader = BufferedReader(InputStreamReader(process.errorStream))

            val output = StringBuilder()
            val errorOutput = StringBuilder()

            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }
            while (errorReader.readLine().also { line = it } != null) {
                errorOutput.append(line).append("\n")
            }

            reader.close()
            errorReader.close()

            val exitCode = process.waitFor()

            if (errorOutput.isNotEmpty()) {
                Log.w(TAG, "Command stderr: ${errorOutput.toString().trim()}")
            }

            Pair(exitCode, output.toString().trim())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to execute root command: $command", e)
            Pair(-1, "")
        }
    }

    fun executeRootCommandSuccess(command: String): Boolean {
        val (exitCode, _) = executeRootCommand(command)
        return exitCode == 0
    }

    fun executeRootCommandOutput(command: String): String {
        val (_, output) = executeRootCommand(command)
        return output
    }
}
