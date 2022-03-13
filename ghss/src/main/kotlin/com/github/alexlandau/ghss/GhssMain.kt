package com.github.alexlandau.ghss

import java.io.File
import java.util.*

fun main(args: Array<String>) {
    try {
        val options: Options = readArgs(args)
        if (options.help) {
            printHelp()
        } else if (options.version) {
            printVersion()
        } else {
            runGhss(options, File("."))
        }
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

fun printHelp() {
    println("ghss - Tool for syncing stacked git commits to stacked GitHub PRs")
    println("TODO: Write useful help -- in most cases just run to push")
}

fun printVersion() {
    println("Early alpha version of ghss -- no versioning scheme yet!")
}

// TODO: Check that nothing is staged before starting
// TODO: Learn about .git/sequencer, maybe I can use that for something cool?
// TODO: I think EDITOR="mv rebaseInstructions" will work to set up an interactive rebase
// TODO: Deal with the issue around commit reordering
// TODO: Let the user choose whether to push as drafts
// TODO: Avoid errors around empty commits (including when renaming them)
fun runGhss(options: Options, repoDir: File) {
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
    warnIfNotDeleteBranchOnMerge(repoDir)
    val gh = RealGh(repoDir)
    val config = loadConfig(repoDir, gh)

    println("(2/4) Fetching from origin...")
    getCommandOutput(listOf("git", "fetch", "origin"), repoDir)

    println("(3/4) Checking the state of the local branch...")
    val diagnosis = getDiagnosis(repoDir, gh)
    val actionPlan = getActionToTake(diagnosis, options)

    val unused: Unit = when (actionPlan) {
        ActionPlan.AddBranchNames -> println("Evaluation: Some commits lack branch names")
        ActionPlan.ReadyToPush -> println("Evaluation: We are ready to push changes")
        is ActionPlan.ReconcileCommits -> println("Evaluation: Some commits need to be reconciled")
        ActionPlan.NothingToPush -> println("Evaluation: The remote branches are up-to-date")
    }

    // TODO: Actually carry out the relevant actions
    if (actionPlan == ActionPlan.AddBranchNames) {
        addBranchNames(diagnosis, config, repoDir)
        // Proceed to push if appropriate (accounting for modified commit hashes)
        val followupDiagnosis = getDiagnosis(repoDir, gh)
        val followupActionPlan = getActionToTake(followupDiagnosis, options)
        if (followupActionPlan == ActionPlan.ReadyToPush) {
            println("(4/4) Pushing to GitHub and updating PRs...")
            pushAndManagePrs(followupDiagnosis, repoDir, gh)
        }
    } else if (actionPlan == ActionPlan.ReadyToPush) {
        println("(4/4) Pushing to GitHub and updating PRs...")
        pushAndManagePrs(diagnosis, repoDir, gh)
    } else if (actionPlan == ActionPlan.ReconcileCommits) {
        reconcileCommits(diagnosis, repoDir)
    }
}

fun reconcileCommits(diagnosis: Diagnosis, repoDir: File) {
    println("Some PRs have been changed on GitHub since you last pushed:")
    for (commit in diagnosis.commits) {
        if (commit.remoteHash != null && commit.remoteHash != commit.previouslyPushedHash) {
            println("  - ${commit.shortHash} ${commit.title}")
        }
    }
    println("\nNot pushing to avoid overwriting these changes.")
    // TODO: Add --force, --pull options
}

fun warnIfNotDeleteBranchOnMerge(repoDir: File) {
    val deleteBranchOnMergeResult = getCommandOutput(listOf(
        "gh", "repo", "view",
        "--json=deleteBranchOnMerge",
        "--jq=.deleteBranchOnMerge"
    ), repoDir).trim()
    if (deleteBranchOnMergeResult == "false") {
        println("Warning: This repository is not configured with the 'Automatically delete head branches' option. " +
                "After merging a PR, you may need to delete its branch manually to update follow-up PRs to target " +
                "the correct branch. If you are an owner of the repo, consider enabling this option under Settings " +
                "-> General -> Pull Requests.")
    }
}

fun addBranchNames(diagnosis: Diagnosis, config: Config, repoDir: File) {
    val prefixToAdd = config.prefix?.let { it.removeSuffix("/") }?.ifBlank { null }
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
                // TODO: Check for invalid characters
                if (input == "?") {
                    println("Special commands are:")
                    println("  a - Autogenerate a branch name from the commit title")
                    println("  x - Exit the CLI without making changes")
                    println("  ? - Display this help message")
                } else if (input == "a") {
                    val branchName = autogenerateBranchName(commit.title, prefixToAdd, repoDir, branchNamesToAdd.values.toSet())
                    println("  The branch name is: $branchName")
                    branchNamesToAdd.put(commit.fullHash, branchName)
                    break
                } else if (input == "x") {
                    return
                } else if (input == "") {
                    println("  Please enter a branch name (or use 'x' to abort and exit)")
                    continue
                } else {
                    val branchName = if (prefixToAdd != null && !input.startsWith("$prefixToAdd/")) {
                        "$prefixToAdd/$input"
                    } else {
                        input
                    }
                    branchNamesToAdd.put(commit.fullHash, branchName)
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
private fun autogenerateBranchName(commitTitle: String, prefix: String?, repoDir: File, alreadyPickedBranchNames: Set<String>): String {
    return autogenerateBranchName(commitTitle, prefix, { branchName ->
        if (alreadyPickedBranchNames.contains(branchName)) {
            return@autogenerateBranchName true
        }
        // Check if the remote has a branch with this name
        val output = getCommandOutput(listOf("git", "branch", "--list", "-r", "origin/$branchName"), repoDir)
        !output.isBlank()
    })
}
internal fun autogenerateBranchName(commitTitle: String, prefix: String?, isBranchNameTaken: (String) -> Boolean): String {
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
    val prefixString = prefix?.let { "$it/" } ?: ""
    val initialBranchName = prefixString + sb.toString().take(40).removeSuffix("-")
    if (!isValidGhBranchName(initialBranchName)) {
        throw GhssException("The branch name auto-generation somehow created a name that looks invalid.\n" +
                "    Input (the commit title): $commitTitle\n" +
                "    Generated branch name: $initialBranchName")
    }
    if (!isBranchNameTaken(initialBranchName)) {
        return initialBranchName
    }
    var suffixNum = 2
    while (true) {
        val candidateName = "$initialBranchName-$suffixNum"
        if (!isBranchNameTaken(candidateName)) {
            if (!isValidGhBranchName(candidateName)) {
                throw GhssException("The branch name auto-generation somehow created a name that looks invalid.\n" +
                        "    Input (the commit title): $commitTitle\n" +
                        "    Generated branch name: $candidateName")
            }
            return candidateName
        }
        suffixNum++
        if (suffixNum >= 100) {
            throw GhssException("There are too many branch names starting with '$initialBranchName'. " +
                    "Try manually picking a more descriptive branch name here.")
        }
    }
}

internal fun isValidGhBranchName(branchName: String): Boolean {
    // TODO: I haven't actually looked up the actual GH branch name requirements
    // See: https://git-scm.com/docs/git-check-ref-format
    // But... we also probably want to be somewhat stricter just for sanity
    // See also: https://docs.github.com/en/get-started/using-git/dealing-with-special-characters-in-branch-and-tag-names#naming-branches-and-tags
    if (branchName.isEmpty()) {
        return false
    }
    // TODO: Figure out an actual limit? Should probably be more lenient than the autogen limit
    if (branchName.chars().count() > 100) {
        return false
    }
    if (!branchName.chars().allMatch(::isValidGhBranchChar)) {
        return false
    }
    if (branchName.split('/', '-').any { it.isEmpty() }) {
        return false
    }
    return true
}

private fun isValidGhBranchChar(char: Int): Boolean {
    return Character.isLetterOrDigit(char)
            || char == '-'.code
            || char == '_'.code
            || char == '/'.code
            || char == '.'.code
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
    val createdPrNumbersByFullHash = HashMap<String, Int>()
    for (commit in diagnosis.commits) {
        val rawBody = getCommitBody(commit.fullHash, repoDir)
        val body = rawBody.lines().filterNot { it.startsWith("gh-branch: ") }.joinToString("\n").trim() +
                getTableSuffix(diagnosis, commit.fullHash)
        if (commit.prNumber == null) {
            val created = gh.createPr(
                title = commit.title,
                body = body,
                baseBranch = lastCommitBranch,
                headBranch = commit.ghBranchTag!!
            )
            createdPrNumbersByFullHash.put(commit.fullHash, created.prNumber)
            println("Created a PR for ${commit.shortHash} ${commit.title}")
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
    if (diagnosis.commits.size >= 2 && createdPrNumbersByFullHash.isNotEmpty()) {
        // Fix the commit bodies
        for (commit in diagnosis.commits) {
            val prNumber = commit.prNumber ?: createdPrNumbersByFullHash[commit.fullHash]
            if (prNumber == null) {
                println("Warning: Could not fix the table of commits due to no PR number for ${commit.shortHash} ${commit.title}")
            }
            val rawBody = getCommitBody(commit.fullHash, repoDir)
            val body = rawBody.lines().filterNot { it.startsWith("gh-branch: ") }.joinToString("\n").trim() +
                    getTableSuffix(diagnosis, commit.fullHash, createdPrNumbersByFullHash)
            gh.editPrBody(
                prNumber = commit.prNumber ?: createdPrNumbersByFullHash[commit.fullHash] ?: error("No PR number for commit ${commit.shortHash} ${commit.title}"),
                body = body,
            )
        }
    }
}

private fun getTableSuffix(diagnosis: Diagnosis, currentCommitFullHash: String, createdPrNumbersByFullHash: Map<String, Int> = mapOf()): String {
    if (diagnosis.commits.size <= 1) {
        return ""
    }

    val sb = StringBuilder()
    sb.append("\n\n")
    sb.append("| Commit stack | PRs |\n")
    sb.append("| --- | --- |\n")

    for (commit in diagnosis.commits) {
        val prNumber = commit.prNumber ?: createdPrNumbersByFullHash[commit.fullHash]
        val prNumberText = prNumber?.let { "#$it" } ?: "PR TBD"
        if (commit.fullHash == currentCommitFullHash) {
            sb.append("| **`${commit.shortHash}`** **${commit.title}** | **$prNumberText** |\n")
        } else {
            sb.append("| `${commit.shortHash}` ${commit.title} | $prNumberText |\n")
        }
    }

    return sb.toString()
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
