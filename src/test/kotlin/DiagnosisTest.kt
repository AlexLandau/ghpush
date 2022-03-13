package com.github.alexlandau.ghpush

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

// TODO: Consider a getCommandOutput alternative (should also throw when exit code != 0)
class DiagnosisTest: MockRepoTest() {
    @Test
    fun testDiagnosisEmptyBranch() {
        val diagnosis = getDiagnosis(localRepo, gh)
        assertEquals(Diagnosis(listOf()), diagnosis)
        assertEquals(ActionPlan.NothingToPush, getActionToTake(diagnosis, fromArgs()))
    }

    @Test
    fun testDiagnosisSingleCommit() {
        writeFile(localRepo, "foo.txt", "Hello, world!")
        getCommandOutput(listOf("git", "add", "."), localRepo)
        getCommandOutput(listOf("git", "commit", "-m", "Add one file"), localRepo)

        val diagnosis = getDiagnosis(localRepo, gh)
        assertEquals(
            Diagnosis(listOf(
                CommitDiagnosis(
                    fullHash = "1",
                    shortHash = "2",
                    title = "Add one file",
                    ghBranchTag = null,
                    remoteHash = null,
                    previouslyPushedHash = null,
                    prNumber = null,
                    prStatus = null
                )
            )),
            normalizeHashes(diagnosis)
        )
        assertEquals(ActionPlan.AddBranchNames, getActionToTake(diagnosis, fromArgs()))
    }

    @Test
    fun testDiagnosisMultipleNewCommits() {
        writeFile(localRepo, "foo.txt", "Hello, Bob.\n")
        getCommandOutput(listOf("git", "add", "."), localRepo)
        getCommandOutput(listOf("git", "commit", "-m", "Add one file"), localRepo)

        writeFile(localRepo, "foo.txt", "Hello, Bob.\nHello, Alice!\n")
        getCommandOutput(listOf("git", "add", "."), localRepo)
        getCommandOutput(listOf("git", "commit", "-m", "Edit the file"), localRepo)

        writeFile(localRepo, "bar.txt", "Buffalo buffalo Buffalo buffalo buffalo buffalo Buffalo buffalo.")
        getCommandOutput(listOf("git", "add", "."), localRepo)
        getCommandOutput(listOf("git", "commit", "-m", "Add a completely valid sentence"), localRepo)

        val diagnosis = getDiagnosis(localRepo, gh)
        assertEquals(
            Diagnosis(listOf(
                CommitDiagnosis(
                    fullHash = "1",
                    shortHash = "2",
                    title = "Add one file",
                    ghBranchTag = null,
                    remoteHash = null,
                    previouslyPushedHash = null,
                    prNumber = null,
                    prStatus = null,
                ),
                CommitDiagnosis (
                    fullHash = "3",
                    shortHash = "4",
                    title = "Edit the file",
                    ghBranchTag = null,
                    remoteHash = null,
                    previouslyPushedHash = null,
                    prNumber = null,
                    prStatus = null,
                ),
                CommitDiagnosis (
                    fullHash = "5",
                    shortHash = "6",
                    title = "Add a completely valid sentence",
                    ghBranchTag = null,
                    remoteHash = null,
                    previouslyPushedHash = null,
                    prNumber = null,
                    prStatus = null,
                ),
            )),
            normalizeHashes(diagnosis)
        )
        assertEquals(ActionPlan.AddBranchNames, getActionToTake(diagnosis, fromArgs()))
    }

    @Test
    fun testDiagnosisNewCommitWithGhBranch() {
        writeFile(localRepo, "foo.txt", "Hello, world!")
        getCommandOutput(listOf("git", "add", "."), localRepo)
        getCommandOutput(listOf("git", "commit", "-m", "Add one file\n\ngh-branch: add-foo"), localRepo)

        val diagnosis = getDiagnosis(localRepo, gh)
        assertEquals(
            Diagnosis(listOf(
                CommitDiagnosis(
                    fullHash = "1",
                    shortHash = "2",
                    title = "Add one file",
                    ghBranchTag = "add-foo",
                    remoteHash = null,
                    previouslyPushedHash = null,
                    prNumber = null,
                    prStatus = null
                )
            )),
            normalizeHashes(diagnosis)
        )
        assertEquals(ActionPlan.ReadyToPush, getActionToTake(diagnosis, fromArgs()))
    }

    @Test
    fun testDiagnosisExistingUnchangedCommit() {
        writeFile(localRepo, "foo.txt", "Hello, world!")
        getCommandOutput(listOf("git", "add", "."), localRepo)
        getCommandOutput(listOf("git", "commit", "-m", "Add one file\n\ngh-branch: add-foo"), localRepo)

        // TODO: Version of this with pushAndManagePrs instead
        getCommandOutput(listOf("git", "push", "origin", "HEAD:add-foo"), localRepo)

        val diagnosis = getDiagnosis(localRepo, gh)
        assertEquals(
            Diagnosis(listOf(
                CommitDiagnosis(
                    fullHash = "1",
                    shortHash = "2",
                    title = "Add one file",
                    ghBranchTag = "add-foo",
                    remoteHash = "1",
                    previouslyPushedHash = null,
                    prNumber = null,
                    prStatus = null
                )
            )),
            normalizeHashes(diagnosis)
        )
        assertEquals(ActionPlan.NothingToPush, getActionToTake(diagnosis, fromArgs()))
    }

    @Test
    fun testDiagnosisFindsPreviouslyPushedHash() {
        writeFile(localRepo, "foo.txt", "Hello, world!")
        getCommandOutput(listOf("git", "add", "."), localRepo)
        getCommandOutput(listOf("git", "commit", "-m", "Add one file\n\ngh-branch: add-foo"), localRepo)
        writeFile(localRepo, "foo.txt", "Hello, world!!!")
        getCommandOutput(listOf("git", "commit", "-a", "-m", "Add another file\n\ngh-branch: add-more"), localRepo)

        val firstDiagnosis = getDiagnosis(localRepo, gh)
        pushAndManagePrs(firstDiagnosis, localRepo, gh)
//        getCommandOutput(listOf("git", "push", "origin", "HEAD:add-foo"), localRepo)

        val diagnosis = getDiagnosis(localRepo, gh)
        assertEquals(
            Diagnosis(listOf(
                CommitDiagnosis(
                    fullHash = "1",
                    shortHash = "2",
                    title = "Add one file",
                    ghBranchTag = "add-foo",
                    remoteHash = "1",
                    previouslyPushedHash = "1",
                    prNumber = 1,
                    prStatus = null
                ),
                CommitDiagnosis(
                    fullHash = "3",
                    shortHash = "4",
                    title = "Add another file",
                    ghBranchTag = "add-more",
                    remoteHash = "3",
                    previouslyPushedHash = "3",
                    prNumber = 2,
                    prStatus = null
                )
            )),
            normalizeHashes(diagnosis)
        )
        assertEquals(ActionPlan.NothingToPush, getActionToTake(diagnosis, fromArgs()))
    }


    @Test
    fun testBranchModifiedUpstream() {
        writeFile(localRepo, "foo.txt", "Hello, world!")
        getCommandOutput(listOf("git", "add", "."), localRepo)
        getCommandOutput(listOf("git", "commit", "-m", "Add one file\n\ngh-branch: add-foo"), localRepo)

        val firstDiagnosis = getDiagnosis(localRepo, gh)
        pushAndManagePrs(firstDiagnosis, localRepo, gh)

        // Amend the commit upstream
        getCommandOutput(listOf("git", "checkout", "add-foo"), originRepo)
        writeFile(originRepo, "foo.txt", "Hello, moon!")
        getCommandOutput(listOf("git", "add", "."), originRepo)
        getCommandOutput(listOf("git", "commit", "--amend", "-m", "Add one file\n\ngh-branch: add-foo"), originRepo)

        // TODO: Variant where we also have local changes here
        getCommandOutput(listOf("git", "fetch"), localRepo)

        val diagnosis = getDiagnosis(localRepo, gh)
        assertEquals(
            Diagnosis(listOf(
                CommitDiagnosis(
                    fullHash = "1",
                    shortHash = "2",
                    title = "Add one file",
                    ghBranchTag = "add-foo",
                    remoteHash = "3",
                    previouslyPushedHash = "1",
                    prNumber = 1,
                    prStatus = null
                ),
            )),
            normalizeHashes(diagnosis)
        )
        assertEquals(ActionPlan.ReconcileCommits, getActionToTake(diagnosis, fromArgs()))
    }

    @Test
    fun testBranchModifiedUpstreamWithForce() {
        writeFile(localRepo, "foo.txt", "Hello, world!")
        getCommandOutput(listOf("git", "add", "."), localRepo)
        getCommandOutput(listOf("git", "commit", "-m", "Add one file\n\ngh-branch: add-foo"), localRepo)

        val firstDiagnosis = getDiagnosis(localRepo, gh)
        pushAndManagePrs(firstDiagnosis, localRepo, gh)

        // Amend the commit upstream
        getCommandOutput(listOf("git", "checkout", "add-foo"), originRepo)
        writeFile(originRepo, "foo.txt", "Hello, moon!")
        getCommandOutput(listOf("git", "add", "."), originRepo)
        getCommandOutput(listOf("git", "commit", "--amend", "-m", "Add one file\n\ngh-branch: add-foo"), originRepo)

        // TODO: Variant where we also have local changes here
        getCommandOutput(listOf("git", "fetch"), localRepo)

        val diagnosis = getDiagnosis(localRepo, gh)
        assertEquals(
            Diagnosis(listOf(
                CommitDiagnosis(
                    fullHash = "1",
                    shortHash = "2",
                    title = "Add one file",
                    ghBranchTag = "add-foo",
                    remoteHash = "3",
                    previouslyPushedHash = "1",
                    prNumber = 1,
                    prStatus = null
                ),
            )),
            normalizeHashes(diagnosis)
        )
        assertEquals(ActionPlan.ReadyToPush, getActionToTake(diagnosis, fromArgs("--force")))
    }

    private fun normalizeHashes(diagnosis: Diagnosis): Diagnosis {
        val hashesMap = HashMap<String, String>()
        return Diagnosis(diagnosis.commits.map { normalizeHashes(it, hashesMap) })
    }

    private fun normalizeHashes(commit: CommitDiagnosis, hashesMap: MutableMap<String, String>): CommitDiagnosis {
        val fullHash = normalizeHash(commit.fullHash, hashesMap)
        val shortHash = normalizeHash(commit.shortHash, hashesMap)
        val remoteHash = commit.remoteHash?.let { normalizeHash(it, hashesMap) }
        val previouslyPushedHash = commit.previouslyPushedHash?.let { normalizeHash(it, hashesMap) }
        return commit.copy(
            fullHash = fullHash,
            shortHash = shortHash,
            remoteHash = remoteHash,
            previouslyPushedHash = previouslyPushedHash
        )
    }

    private fun normalizeHash(hash: String, hashesMap: MutableMap<String, String>): String {
        return hashesMap.computeIfAbsent(hash, { input -> (hashesMap.size + 1).toString() })
    }
}