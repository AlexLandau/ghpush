package com.github.alexlandau.ghpush

import java.io.File

/**
 * Represents configuration derived from git config files.
 *
 * TODO: Add ability to check in certain config, something like .ghpush.conf
 */
data class Config (
    /**
     * Whether to push all commits as draft commits by default.
     */
    val draft: Boolean,
    /**
     * The prefix, if any, to put before new branch names automatically.
     *
     * This comes from "ghpush.prefix" in the git config. The special values "email" or "username" will have been
     * translated before use in this variable.
     */
    val prefix: String?,
)

fun loadConfig(repoDir: File, gh: Gh): Config {
    var draft: Boolean = false
    var prefix: String? = null

    val getConfigResult = exec(listOf("git", "config", "--get-regexp", "ghpush\\."), repoDir)
    val configLines = if (getConfigResult.exitValue != 0) {
        ""
    } else {
        getConfigResult.stdOut
    }
    for (line in configLines.lines()) {
        if (line.isBlank()) {
            continue
        }
        val (key, value) = line.split(" ", limit = 2)
        when (key) {
            "ghpush.draft" -> draft = (value.lowercase() == "true")
            "ghpush.prefix" -> prefix = value.trim()
            else -> println("Warning: Key '${key}' in git config is unknown to ghpush. Did you mistype something? Is your version of ghpush up-to-date? Try: 'git help config'")
        }
    }

    // Once we have the final values from the config (since there may be multiple lines per key), apply transformations
    prefix = interpretPrefix(prefix, repoDir, gh)

    return Config(
        draft = draft,
        prefix = prefix,
    )
}

// TODO: Filter dubious characters out of the prefix
fun interpretPrefix(value: String?, repoDir: File, gh: Gh): String? {
    if (value == null) {
        return null
    } else if (value == "email") {
        val emailString = getCommandOutput(listOf("git", "config", "--get", "user.email"), repoDir).trim()
        if (!emailString.contains("@")) {
            println("Warning: Config option 'ghpush.prefix' is set to 'email' but the result of " +
                    "'git config --get user.email' is not set or does not look like an email address. Try: " +
                    "'git help config'")
            return null
        }
        return emailString.split("@", limit = 2)[0]
    } else if (value == "username") {
        return gh.getUserLogin()
    } else {
        return value
    }
}
