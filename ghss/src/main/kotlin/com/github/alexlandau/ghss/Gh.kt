package com.github.alexlandau.ghss

import com.fasterxml.jackson.databind.ObjectMapper
import java.io.File

/**
 * We can use real git repos and the real git CLI for testing, but we can't reasonably use real
 * GitHub servers in testing. Instead, we put our interactions with GitHub in a mockable interface.
 * See MockGh for the mocked implementation.
 */
interface Gh {
    fun findPrNumber(ghBranchName: String): Int?
    fun createPr(title: String, body: String, baseBranch: String, headBranch: String)
    fun editPr(prNumber: Int, title: String, body: String, baseBranch: String)
}

class RealGh(private val repoPath: File): Gh {
    override fun findPrNumber(ghBranchName: String): Int? {
        // TODO: Check that this actually works, then post-filter (?) the output
        // TODO: Try using the template feature to remove the need to parse JSON
        val outputJson = getCommandOutput(listOf("gh", "pr", "list", "--json=headRefName,number", "--head", ghBranchName), repoPath).trim()
        if (outputJson == "[]") {
            return null
        }
        val mapper = ObjectMapper()
        val arrayNode = mapper.readTree(outputJson)
        for (node in arrayNode) {
            // TODO: Reconcile the correct behavior I'm seeing now with the behavior I remember seeing before
            if (node["headRefName"].asText() == ghBranchName) {
                return node["number"].asInt()
            }
        }
        return null
    }

    override fun createPr(title: String, body: String, baseBranch: String, headBranch: String) {
        val prCreateOutput = getCommandOutput(
            listOf(
                "gh", "pr", "create",
                "--title", title,
                "--body", body,
                "--base", baseBranch,
                "--head", headBranch
            ), repoPath
        )
        // Output is the URL of the created PR
        println(prCreateOutput.trim())

    }

    override fun editPr(prNumber: Int, title: String, body: String, baseBranch: String) {
        val prEditOutput = getCommandOutput(
            listOf(
                "gh", "pr", "edit",
                prNumber.toString(),
                "--title", title,
                "--body", body,
                "--base", baseBranch
            ), repoPath
        )
        println("prEditOutput: $prEditOutput")
    }
}
