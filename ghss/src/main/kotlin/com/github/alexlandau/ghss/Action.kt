package com.github.alexlandau.ghss

sealed class ActionPlan {
    /**
     * Indicates all commits have incorporated any upstream updates and have gh-branch names chosen.
     */
    object ReadyToPush: ActionPlan()
    /**
     * Indicates some commits need to have gh-branch names added.
     */
    object AddBranchNames: ActionPlan()
    /**
     * Indicates a commit needs updates. Contains the local hash and the remote hash.
     */
    data class ReconcileCommits(val localHash: String, val remoteHash: String): ActionPlan()
}

fun getActionToTake(diagnosis: Diagnosis): ActionPlan {
    for (commit in diagnosis.commits) {
        if (commit.remoteHash != null && commit.remoteHash != commit.fullHash) {
            return ActionPlan.ReconcileCommits(commit.fullHash, commit.remoteHash)
        }
    }
    for (commit in diagnosis.commits) {
        if (commit.ghBranchTag == null) {
            return ActionPlan.AddBranchNames
        }
    }
    return ActionPlan.ReadyToPush
}