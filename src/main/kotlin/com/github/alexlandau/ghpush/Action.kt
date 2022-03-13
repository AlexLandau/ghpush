package com.github.alexlandau.ghpush

sealed class ActionPlan {
    /**
     * Indicates all current commits have been pushed already.
     */
    object NothingToPush: ActionPlan()
    /**
     * Indicates all commits have incorporated any upstream updates and have gh-branch names chosen.
     */
    object ReadyToPush: ActionPlan()
    /**
     * Indicates some commits need to have gh-branch names added.
     */
    object AddBranchNames: ActionPlan()
    /**
     * Indicates at least one already-pushed commit has been changed upstream, and we should try to incorporate
     * those changes.
     */
    object ReconcileCommits: ActionPlan()
}

fun getActionToTake(diagnosis: Diagnosis, options: Options): ActionPlan {
    for (commit in diagnosis.commits) {
        // TODO: Reconsider case where remoteHash == fullHash but previouslyPushedHash is different and non-null
        if (commit.remoteHash != null
            && commit.remoteHash != commit.fullHash
            && commit.remoteHash != commit.previouslyPushedHash
            && !options.force) {
            return ActionPlan.ReconcileCommits
        }
    }
    for (commit in diagnosis.commits) {
        if (commit.ghBranchTag == null) {
            return ActionPlan.AddBranchNames
        }
    }
    for (commit in diagnosis.commits) {
        if (commit.remoteHash != commit.fullHash) {
            return ActionPlan.ReadyToPush
        }
    }
    return ActionPlan.NothingToPush
}