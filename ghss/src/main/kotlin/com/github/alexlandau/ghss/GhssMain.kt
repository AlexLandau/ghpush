package com.github.alexlandau.ghss

import java.io.File
import java.nio.charset.StandardCharsets
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
// TODO: I think EDITOR="mv rebaseInstructions" will work to set up an interactive rebase
// TODO: Deal with the issue around commit reordering
// TODO: Let the user choose whether to push as drafts
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
    val gh = RealGh(repoDir)

    println("(2/4) Fetching from origin...")
    getCommandOutput(listOf("git", "fetch", "origin"), repoDir)

    println("(3/4) Checking the state of the local branch...")
    val diagnosis = getDiagnosis(repoDir, gh)
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
    } else if (actionPlan == ActionPlan.ReadyToPush) {
        pushAndManagePrs(diagnosis, repoDir, gh)
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
                    val branchName = autogenerateBranchName(commit.title, repoDir, branchNamesToAdd.values.toSet())
                    println("  The branch name is: $branchName")
                    branchNamesToAdd.put(commit.fullHash, branchName)
                    break
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

// TODO: Also disallow branch names picked earlier
private fun autogenerateBranchName(commitTitle: String, repoDir: File, alreadyPickedBranchNames: Set<String>): String {
    return autogenerateBranchName(commitTitle, { branchName ->
        if (alreadyPickedBranchNames.contains(branchName)) {
            return@autogenerateBranchName true
        }
        // Check if the remote has a branch with this name
        val output = getCommandOutput(listOf("git", "branch", "--list", "-r", "origin/$branchName"), repoDir)
        !output.isBlank()
    })
}
internal fun autogenerateBranchName(commitTitle: String, isBranchNameTaken: (String) -> Boolean): String {
    val withHyphensArray = commitTitle.codePoints().map { codePoint ->
        if (!Character.isLetterOrDigit(codePoint)) {
            '-'.code
        } else {
            Character.toLowerCase(codePoint)
        }
    }.toArray()
    val withHyphens = String(withHyphensArray, 0, withHyphensArray.size)
    val sb = StringBuilder()
    var lastCharWasAHyphen = true
    for (c in withHyphens) {
        if (c == '-') {
            if (!lastCharWasAHyphen) {
                sb.append(c)
            }
            lastCharWasAHyphen = true
        } else {
            sb.append(c)
            lastCharWasAHyphen = false
        }
    }
    val initialBranchName = sb.toString().take(40).removeSuffix("-")
    if (!isBranchNameTaken(initialBranchName)) {
        return initialBranchName
    }
    var suffixNum = 2
    while (true) {
        val candidateName = "$initialBranchName-$suffixNum"
        if (!isBranchNameTaken(candidateName)) {
            return candidateName
        }
        suffixNum++
        if (suffixNum >= 100) {
            throw GhssException("There are too many branch names starting with '$initialBranchName'. " +
                    "Try manually picking a more descriptive branch name here.")
        }
    }
}

fun pushAndManagePrs(diagnosis: Diagnosis, repoDir: File, gh: Gh) {
    val pushCommand = ArrayList<String>()
    pushCommand.add("git")
    pushCommand.add("push")
    pushCommand.add("origin")
    for (commit in diagnosis.commits) {
        // TODO: Switch to using --force-with-lease
        pushCommand.add("+${commit.fullHash}:refs/heads/${commit.ghBranchTag}")
    }
    getCommandOutput(pushCommand, repoDir)

    for (commit in diagnosis.commits) {
        // Record what we pushed, so future invocations can tell if the branch changed upstream
        val trackerBranchName = "ghss/pushed-to/develop/${commit.ghBranchTag}"
        val startPoint = commit.fullHash
        getCommandOutput(listOf("git", "branch", "--no-track", "-f", trackerBranchName, startPoint), repoDir)
    }

    var lastCommitBranch = "develop" // TODO: Make configurable
    for (commit in diagnosis.commits) {
        val rawBody = getCommitBody(commit.fullHash, repoDir)
        val body = rawBody.lines().filterNot { it.startsWith("gh-branch: ") }.joinToString("\n").trim()
        if (commit.prNumber == null) {
            gh.createPr(
                title = commit.title,
                body = body,
                baseBranch = lastCommitBranch,
                headBranch = commit.ghBranchTag!!
            )
        } else {
            // Handle existing PRs
            gh.editPr(
                prNumber = commit.prNumber,
                title = commit.title,
                body = body,
                baseBranch = lastCommitBranch
            )
        }
        lastCommitBranch = commit.ghBranchTag!!
    }
}

internal fun getCommitBody(hash: String, repoDir: File): String {
    // git log -n 1 --format=format:%b <HASH>
    return getCommandOutput(listOf("git", "log", "-n", "1", "--format=format:%b", hash), repoDir).trim()
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
