pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://api.xposed.info/")
    }
}

plugins {
    id("com.highcapable.gropify") version "1.0.1"
}

val gitHash: String by lazy { runGitCommand("rev-parse", "--short", "HEAD") ?: "unknown" }
val gitBranch: String by lazy {
    val url = runGitCommand("remote", "get-url", "origin") ?: "unknown"
    val branch = runGitCommand("branch", "--show-current") ?: "unknown"
    """github\.com[:/](.+?)(\.git)?$""".toRegex().find(url)?.groupValues?.get(1).orEmpty() + "/" + branch
}
val gitCommitCount: Int by lazy { runGitCommand("rev-list", "--count", "HEAD")?.toIntOrNull() ?: 0 }
val gitVersionCode: Int by lazy { 5 + gitCommitCount }
fun runGitCommand(vararg args: String): String? = runCatching {
    ProcessBuilder(listOf("git") + args)
        .redirectErrorStream(true)
        .start()
        .let { process ->
            val output = process.inputStream.bufferedReader().readText().trim()
            if (process.waitFor() == 0 && output.isNotBlank()) output else null
        }
}.getOrNull()

gropify {
    rootProject {
        common {
            isEnabled = false
        }
    }

    projects(":app") {
        android {
            isEnabled = true
            val buildSuffix = providers.gradleProperty("buildSuffix").orNull as? String ?: "dev"

            permanentKeyValues(
                "git.hash" to gitHash,
                "git.branch" to gitBranch,
                "build.number" to gitVersionCode,
                "build.channel" to buildSuffix
            )
        }
    }
}

rootProject.name = "REAREye"

include(":app")