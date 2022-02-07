package com.github.alexlandau.ghss

import java.io.File
import java.util.*

fun main() {
    try {
        runGhss(File("."))
    } catch (e: GhssException) {
        println(e.messageToUser)
        System.exit(1)
    } catch (e: Exception) {
        println()
        e.printStackTrace(System.out)
        println()
        println("ghss encountered an unhandled error (stacktrace above). Try upgrading if you are not on the latest " +
                "version. If the problem persists, check https://github.com/AlexLandau/ghss for matching error " +
                "reports and consider filing an issue if this error has not been reported.")
        System.exit(1)
    }
}

// TODO: Check that nothing is staged before starting
// TODO: Learn about .git/sequencer, maybe I can use that for something cool?
fun runGhss(repoDir: File) {
    println("(1/4) Checking preconditions...")
    // TODO: Specially handle the cases where these aren't installed
    val gitVersion = getCommandOutput(listOf("git", "--version"), repoDir).trim()
    println("Git version is ${gitVersion}, pretend we checked that")
    // TODO: Figure out a minimum git version and check against that
    val ghVersion = getCommandOutput(listOf("gh", "--version"), repoDir).trim()
    println("Gh CLI version is ${ghVersion}, pretend we checked that")
    val gitRemoteUrl = getGitOrigin(repoDir)
    println("GitHub hostname is $gitRemoteUrl")
    val isLoggedIntoGh = isLoggedIntoGh(gitRemoteUrl, repoDir)
    println("isLoggedIntoGh: $isLoggedIntoGh")
    if (!isLoggedIntoGh) {
        throw GhssException("You must be logged into the gh cli to use ghsync. Run: gh auth login --hostname $gitRemoteUrl")
    }

    println("(2/4) Fetching from origin...")
    getCommandOutput(listOf("git", "fetch", "origin"), repoDir)

    println("(3/4) Checking the state of the local branch...")
    val diagnosis = getDiagnosis(repoDir)
    val actionPlan = getActionToTake(diagnosis)

    val unused: Unit = when (actionPlan) {
        ActionPlan.AddBranchNames -> println("Evaluation: Some commits lack branch names")
        ActionPlan.ReadyToPush -> println("Evaluation: We are ready to push changes")
        is ActionPlan.ReconcileCommits -> println("Evaluation: Some commits need to be reconciled")
        ActionPlan.NothingToPush -> println("Evaluation: The remote branches are up-to-date")
    }

    // TODO: Actually carry out the relevant actions
    if (actionPlan == ActionPlan.AddBranchNames) {
        addBranchNames(diagnosis, repoDir)
    }
}

fun addBranchNames(diagnosis: Diagnosis, repoDir: File) {
    // The key is the full hash from the diagnosis
    val branchNamesToAdd = HashMap<String, String>()
    for (commit in diagnosis.commits) {
        if (commit.ghBranchTag == null) {
            while (true) {
                println("For commit ${commit.shortHash} '${commit.title}',")
                print("  choose a branch name (or a/x/?): ")
                System.out.flush()
                val input = readLine()
                if (input == null) {
                    throw GhssException(
                        "Error: Looked for an input from the standard input and couldn't get " +
                                "anything. This may happen if you're piping input from a script and the end of the " +
                                "file has been reached."
                    )
                }
                println("The input was: $input") // (debug)
                // TODO: Check for invalid characters
                if (input == "?") {
                    println("Special commands are:")
                    println("  a - Autogenerate a branch name from the commit title")
                    println("  x - Exit the CLI without making changes")
                    println("  ? - Display this help message")
                } else if (input == "a") {
                    TODO()
                } else if (input == "x") {
                    return
                } else if (input == "") {
                    println("  Please enter a branch name (or use 'x' to abort and exit)")
                    continue
                } else {
                    branchNamesToAdd.put(commit.fullHash, input)
                    break
                }
            }
        }
    }

    println("Branch names to add are: $branchNamesToAdd")
    val originalBranch = getCommandOutput(listOf("git", "branch", "--show-current"), repoDir).trim()
    println("Current branch is: $originalBranch")
    println("Rewriting the branch to add gh-branch to commit messages...")
    // Find the first commit on this list, check out <that commit>~1
    val fullHashesToRewrite = diagnosis.commits.map { it.fullHash }
        .dropWhile { !branchNamesToAdd.containsKey(it) }
    getCommandOutput(listOf("git", "checkout", "${fullHashesToRewrite[0]}~1"), repoDir)
    for (hashToRewrite in fullHashesToRewrite) {
        getCommandOutput(listOf("git", "cherry-pick", hashToRewrite), repoDir)
        if (branchNamesToAdd.containsKey(hashToRewrite)) {
            println("Should rewrite the hash here...")
            // TODO: Maybe check command-line size limits here?
            // If running into limits, can use -F to read from a file or standard input
            // %B?
            val commitMessage = getCommandOutput(listOf("git", "log", "-n", "1", "--format=format:%B"), repoDir)
            val editedMessage = "${commitMessage.trim()}\n\ngh-branch: ${branchNamesToAdd[hashToRewrite]!!}"
            getCommandOutput(listOf("git", "commit", "--amend", "-m", editedMessage), repoDir)
        }
    }
    // Move the original branch name over to the new commit sequence
    if (originalBranch.isNotEmpty()) {
        getCommandOutput(listOf("git", "checkout", "-B", originalBranch), repoDir)
    }
}

internal fun getGitOrigin(repoPath: File): String {
    val fullUrl = getCommandOutput(listOf("git", "remote", "get-url", "origin"), repoPath)
    // e.g. git@github.com:AlexLandau/ghss.git
    val atIndex = fullUrl.indexOf('@')
    val colonIndex = fullUrl.indexOf(':', atIndex)
    if (atIndex < 0 || colonIndex < 0 || colonIndex <= atIndex) {
        throw GhssException("Could not process git remote URL \"${fullUrl}\" (from command 'git remote get-url origin'); was expecting the server between '@' and ':'")
    }
    return fullUrl.substring(atIndex + 1, colonIndex)
}

internal fun isLoggedIntoGh(hostname: String, repoPath: File): Boolean {
    val result = exec(listOf("gh", "auth", "status", "--hostname", hostname), repoPath)
    return result.exitValue == 0
}
