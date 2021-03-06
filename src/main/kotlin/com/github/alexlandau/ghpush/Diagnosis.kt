package com.github.alexlandau.ghpush

import java.io.File


data class Diagnosis(
    /**
     * The branch that the first PR should target. May be specified using --onto, or may be guessed or configured.
     */
    val targetBranch: String,
    /**
     * The commits on the local branch, ordered from earliest to latest
     */
    val commits: List<CommitDiagnosis>,
)

data class CommitDiagnosis(
    /**
     * The full hash (SHA) of the commit. Used internally and when interacting with git and gh.
     */
    val fullHash: String,
    /**
     * The abbreviated hash (SHA) of the commit, as chosen by git. Used in user interactions.
     */
    val shortHash: String,
    /**
     * The title of the commit's commit message (i.e., the first line).
     */
    val title: String,
    /**
     * The contents of the "gh-branch:" line in the commit message, if any.
     */
    val ghBranchTag: String?,
    /**
     * The hash of GitHub's current version of the listed gh-branch, as of when this CLI ran git fetch.
     */
    val remoteHash: String?,
    /**
     * The hash of the commit we previously pushed for this branch, if any, as indicated by the appropriate
     * ghpush/pushed-to/... branch.
     */
    val previouslyPushedHash: String?,
    /**
     * The PR number for the PR opened for this target branch, if any.
     */
    val prNumber: Int?,
    // TODO: Figure out how we want to use this and how we want to represent it (enum maybe?)
    val prStatus: String?,
)

fun getDiagnosis(repoPath: File, options: Options, gh: Gh): Diagnosis {
    val targetBranch = getTargetBranch(repoPath, options, gh)
    val commits = getCommitHashesOnBranch(repoPath, targetBranch)
    val longHashes = commits.map { it.longHash }

    val ghBranchTags = getGhBranchesInCommitMessages(longHashes, repoPath)
    val remoteHashes = getRemoteHashes(ghBranchTags.values, repoPath)

    val diagnosisCommits = commits.map {
        val fullHash = it.longHash
        val shortHash = it.shortHash
        val title = it.title
        val ghBranchTag = ghBranchTags[fullHash]
        val remoteHash = ghBranchTag?.let { remoteHashes[it] }
        val previouslyPushedHash = ghBranchTag?.let { findPreviouslyPushedHash(it, targetBranch, repoPath) }
        val prNumber = ghBranchTag?.let { gh.findOpenPrNumber(ghBranchTag) }
        // TODO: Fill in the nulls here
        CommitDiagnosis(
            fullHash, shortHash, title, ghBranchTag, remoteHash, previouslyPushedHash, prNumber, prStatus = null
        )
    }
    return Diagnosis(targetBranch, diagnosisCommits)
}

fun getTargetBranch(repoPath: File, options: Options, gh: Gh): String {
    if (options.onto != null) {
        // Validate that the branch exists upstream
        val result = exec(listOf("git", "rev-parse", "--verify", "-q", "origin/${options.onto}"), repoPath)
        if (result.exitValue != 0) {
            throw GhpushException("Error: The target branch specified by '--onto' was '${options.onto}', but this " +
                    "branch doesn't exist in the origin repository.")
        }
        return options.onto
    }

    // TODO: Allow repositories to configure the possible values for this branch (e.g. to specify possible release
    // branches as targets for inference)
    return gh.getDefaultBranchRef()
}

fun findPreviouslyPushedHash(ghBranchName: String, targetBranch: String, repoPath: File): String? {
    val result = exec(listOf("git", "rev-parse", "--verify", "-q", "ghpush/pushed-to/origin/${ghBranchName}"), repoPath)
    if (result.exitValue == 0 && result.stdOut.isNotBlank()) {
        return result.stdOut.trim()
    }
    return null
}

fun getRemoteHashes(ghBranchNames: Collection<String>, repoPath: File): Map<String, String> {
    if (ghBranchNames.isEmpty()) {
        return mapOf()
    }
    val command = ArrayList<String>()
    command.add("git")
    command.add("show-ref")
    for (ghBranchName in ghBranchNames) {
        command.add("refs/remotes/origin/${ghBranchName}")
    }

    val showRefResult = exec(command, repoPath)
    // If none match, we get a return value of 1; ignore this error case
    if (showRefResult.exitValue == 1 && showRefResult.stdOut.endsWith(" did not return successfully")) {
        return mapOf()
    }
    val showRefOutput = showRefResult.stdOut

    val result = HashMap<String, String>()
    for (line in showRefOutput.trim().lines()) {
        if (line.isBlank()) {
            continue
        }
        val (hash, branchRef) = line.split(" ", limit = 2)
        if (!branchRef.startsWith("refs/remotes/origin/")) {
            throw GhpushException(
                "Unexpected result from '${command.toCommandString()}'; expected all results to start with 'refs/remotes/origin/':\n${showRefOutput}"
            )
        }
        val ghBranchName = branchRef.removePrefix("refs/remotes/origin/")
        if (result.containsKey(ghBranchName)) {
            throw GhpushException(
                "Unexpected result from '${command.toCommandString()}'; found multiple results with branch name ${ghBranchName}:\n${showRefOutput}"
            )
        }
        result.put(ghBranchName, hash)
    }
    return result
}

data class CommitBasics (
    val shortHash: String,
    val longHash: String,
    val title: String,
)

/**
 * Returns the hashes of our "local branch", with parent commits earlier in the list
 */
fun getCommitHashesOnBranch(repoPath: File, targetBranch: String): List<CommitBasics> {
    val output = getCommandOutput(listOf("git", "log", "--format=format:%h %H %s", "--reverse", "origin/${targetBranch}..HEAD"), repoPath)

    return output.lines().filter { it.isNotBlank() }.map { line ->
        val (shortHash, longHash, title) = line.split(" ", limit = 3)
        CommitBasics(shortHash, longHash, title)
    }
}

fun getGhBranchesInCommitMessages(hashes: List<String>, repoPath: File): Map<String, String> {
    val result = HashMap<String, String>()
    for (hash in hashes) {
//        println("Hash is: ${hash}")
        val output = getCommandOutput(listOf("git", "log", "-n", "1", "--format=format:%b", hash), repoPath)
//        println("Output: ${output}")
        for (line in output.lines()) {
//            println("line: $line")
            if (line.startsWith("gh-branch:")) {
                val branchName = line.removePrefix("gh-branch:").trim()
//                println("branchName: $branchName")
                result.put(hash, branchName)
            }
        }
    }
//    println("Result: $result")
    return result
}