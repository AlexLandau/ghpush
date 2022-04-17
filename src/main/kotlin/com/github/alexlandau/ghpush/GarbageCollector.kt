package com.github.alexlandau.ghpush

import java.io.File

fun collectGarbage(repoDir: File, gh: Gh) {
    val branches = getLocalTrackingBranches(repoDir)
    var deletionCount = 0
    for (branch in branches) {
        if (isUnnecessaryTrackingBranch(branch, repoDir, gh)) {
            run(listOf("git", "branch", "-d", branch), repoDir)
            deletionCount++
        }
    }
    println("Deleted $deletionCount old tracking branches.")
}

fun isUnnecessaryTrackingBranch(branch: String, repoDir: File, gh: Gh): Boolean {
    if (branch.count { it == '/' } < 3) {
        // This doesn't look like a tracking branch; don't touch it
        return false
    }
    val (_, _, remote, ghBranch) = branch.split("/", limit = 4)
    // TODO: Support other remotes
    if (remote != "origin") {
        return false
    }
    // TODO: Can we batch these lookups for efficiency? Maybe not
    return gh.findOpenPrNumber(ghBranch) == null
}

private fun getLocalTrackingBranches(repoDir: File): List<String> {
    val result = getCommandOutput(listOf("git", "branch", "--list", "ghpush/pushed-to/*"), repoDir)
    return result.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
}
