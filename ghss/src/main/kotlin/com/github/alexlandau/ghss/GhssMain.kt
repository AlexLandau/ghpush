package com.github.alexlandau.ghss

import java.io.File

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

    when (actionPlan) {
        ActionPlan.AddBranchNames -> println("Evaluation: Some commits lack branch names")
        ActionPlan.ReadyToPush -> println("Evaluation: We are ready to push changes")
        is ActionPlan.ReconcileCommits -> println("Evaluation: Some commits need to be reconciled")
    }

    // TODO: Actually carry out the relevant actions
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
