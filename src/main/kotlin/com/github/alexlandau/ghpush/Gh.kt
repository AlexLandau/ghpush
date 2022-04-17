package com.github.alexlandau.ghpush

import java.io.File

/**
 * We can use real git repos and the real git CLI for testing, but we can't reasonably use real
 * GitHub servers in testing. Instead, we put our interactions with GitHub in a mockable interface.
 * See MockGh for the mocked implementation.
 */
interface Gh {
    // Note: This only returns open PRs
    fun findOpenPrNumber(ghBranchName: String): Int?
    fun createPr(title: String, body: String, baseBranch: String, headBranch: String): CreatePrResult
    fun editPr(prNumber: Int, title: String, body: String, baseBranch: String): EditPrResult
    fun editPrBody(prNumber: Int, body: String): EditPrResult
    fun getUserLogin(): String
    fun getDefaultBranchRef(): String
}

data class CreatePrResult(val prNumber: Int, val prUrl: String)
data class EditPrResult(val prUrl: String)

class RealGh(private val repoPath: File): Gh {
    override fun findOpenPrNumber(ghBranchName: String): Int? {
        val outputLines = getCommandOutput(listOf("gh", "pr", "list", "--json=headRefName,number",
            "--jq=.[] | (.number | tostring) + \" \" + .headRefName",
            "--head", ghBranchName), repoPath).trim()
        // TODO: Reconcile the correct behavior I'm seeing now with the behavior I remember seeing before
        for (line in outputLines.lines()) {
            if (line.isBlank()) {
                continue
            }
            val (number, headRefName) = line.split(" ", limit = 2)
            if (headRefName == ghBranchName) {
                return number.toInt()
            }
        }
        return null
    }

    override fun createPr(title: String, body: String, baseBranch: String, headBranch: String): CreatePrResult {
        val prCreateOutput = getCommandOutput(
            listOf(
                "gh", "pr", "create",
                "--title", title,
                "--body", body,
                "--base", baseBranch,
                "--head", headBranch
            ), repoPath
        )
        val prUrl = prCreateOutput.trim()
        val prNumber = prUrl.takeLastWhile { it.isDigit() }.toInt()
        return CreatePrResult(prNumber, prUrl)
    }

    override fun editPr(prNumber: Int, title: String, body: String, baseBranch: String): EditPrResult {
        val prEditOutput = getCommandOutput(
            listOf(
                "gh", "pr", "edit",
                prNumber.toString(),
                "--title", title,
                "--body", body,
                "--base", baseBranch
            ), repoPath
        )
        val prUrl = prEditOutput.trim()
        return EditPrResult(prUrl)
    }

    override fun editPrBody(prNumber: Int, body: String): EditPrResult {
        val prEditOutput = getCommandOutput(
            listOf(
                "gh", "pr", "edit",
                prNumber.toString(),
                "--body", body,
            ), repoPath
        )
        val prUrl = prEditOutput.trim()
        return EditPrResult(prUrl)
    }

    override fun getUserLogin(): String {
        // Not sure if getting this from 'gh auth status' would be better; this way doesn't require parsing
        return getCommandOutput(listOf("gh", "api", "user", "--jq", ".login"), repoPath).trim()
    }

    override fun getDefaultBranchRef(): String {
        return getCommandOutput(listOf("gh", "repo", "view", "--json=defaultBranchRef", "--jq=.defaultBranchRef.name"), repoPath).trim()
    }
}
