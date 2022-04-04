package com.github.alexlandau.ghpush

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DiagnosisTest: MockRepoTest() {
    @Test
    fun testDiagnosisEmptyBranch() {
        val options = fromArgs()
        val diagnosis = getDiagnosis(localRepo, options, gh)
        assertThat(diagnosis).isEqualTo(Diagnosis("main", listOf()))
        assertThat(getActionToTake(diagnosis, options)).isEqualTo(ActionPlan.NothingToPush)
    }

    @Test
    fun testDiagnosisSingleCommit() {
        val options = fromArgs()
        writeFile(localRepo, "foo.txt", "Hello, world!")
        run(listOf("git", "add", "."), localRepo)
        run(listOf("git", "commit", "-m", "Add one file"), localRepo)

        val diagnosis = getDiagnosis(localRepo, options, gh)
        assertThat(normalizeHashes(diagnosis)).isEqualTo(
            Diagnosis("main", listOf(
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
            ))
        )
        assertThat(getActionToTake(diagnosis, options)).isEqualTo(ActionPlan.AddBranchNames)
    }

    @Test
    fun testDiagnosisMultipleNewCommits() {
        val options = fromArgs()
        writeFile(localRepo, "foo.txt", "Hello, Bob.\n")
        run(listOf("git", "add", "."), localRepo)
        run(listOf("git", "commit", "-m", "Add one file"), localRepo)

        writeFile(localRepo, "foo.txt", "Hello, Bob.\nHello, Alice!\n")
        run(listOf("git", "add", "."), localRepo)
        run(listOf("git", "commit", "-m", "Edit the file"), localRepo)

        writeFile(localRepo, "bar.txt", "Buffalo buffalo Buffalo buffalo buffalo buffalo Buffalo buffalo.")
        run(listOf("git", "add", "."), localRepo)
        run(listOf("git", "commit", "-m", "Add a completely valid sentence"), localRepo)

        val diagnosis = getDiagnosis(localRepo, options, gh)
        assertThat(normalizeHashes(diagnosis)).isEqualTo(
            Diagnosis("main", listOf(
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
            ))
        )
        assertThat(getActionToTake(diagnosis, options)).isEqualTo(ActionPlan.AddBranchNames)
    }

    @Test
    fun testDiagnosisNewCommitWithGhBranch() {
        val options = fromArgs()
        writeFile(localRepo, "foo.txt", "Hello, world!")
        run(listOf("git", "add", "."), localRepo)
        run(listOf("git", "commit", "-m", "Add one file\n\ngh-branch: add-foo"), localRepo)

        val diagnosis = getDiagnosis(localRepo, options, gh)
        assertThat(normalizeHashes(diagnosis)).isEqualTo(
            Diagnosis("main", listOf(
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
            ))
        )
        assertThat(getActionToTake(diagnosis, options)).isEqualTo(ActionPlan.ReadyToPush)
    }

    @Test
    fun testDiagnosisExistingUnchangedCommit() {
        val options = fromArgs()
        writeFile(localRepo, "foo.txt", "Hello, world!")
        run(listOf("git", "add", "."), localRepo)
        run(listOf("git", "commit", "-m", "Add one file\n\ngh-branch: add-foo"), localRepo)

        // TODO: Version of this with pushAndManagePrs instead
        run(listOf("git", "push", "origin", "HEAD:add-foo"), localRepo)

        val diagnosis = getDiagnosis(localRepo, options, gh)
        assertThat(normalizeHashes(diagnosis)).isEqualTo(
            Diagnosis("main", listOf(
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
            ))
        )
        assertThat(getActionToTake(diagnosis, options)).isEqualTo(ActionPlan.NothingToPush)
    }

    @Test
    fun testDiagnosisFindsPreviouslyPushedHash() {
        val options = fromArgs()
        writeFile(localRepo, "foo.txt", "Hello, world!")
        run(listOf("git", "add", "."), localRepo)
        run(listOf("git", "commit", "-m", "Add one file\n\ngh-branch: add-foo"), localRepo)
        writeFile(localRepo, "foo.txt", "Hello, world!!!")
        run(listOf("git", "commit", "-a", "-m", "Add another file\n\ngh-branch: add-more"), localRepo)

        val firstDiagnosis = getDiagnosis(localRepo, options, gh)
        pushAndManagePrs(firstDiagnosis, localRepo, gh)
//        run(listOf("git", "push", "origin", "HEAD:add-foo"), localRepo)

        val diagnosis = getDiagnosis(localRepo, options, gh)
        assertThat(normalizeHashes(diagnosis)).isEqualTo(
            Diagnosis("main", listOf(
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
            ))
        )
        assertThat(getActionToTake(diagnosis, options)).isEqualTo(ActionPlan.NothingToPush)
    }


    @Test
    fun testBranchModifiedUpstream() {
        val options = fromArgs()
        writeFile(localRepo, "foo.txt", "Hello, world!")
        run(listOf("git", "add", "."), localRepo)
        run(listOf("git", "commit", "-m", "Add one file\n\ngh-branch: add-foo"), localRepo)

        val firstDiagnosis = getDiagnosis(localRepo, options, gh)
        pushAndManagePrs(firstDiagnosis, localRepo, gh)

        // Amend the commit upstream
        run(listOf("git", "checkout", "add-foo"), originRepo)
        writeFile(originRepo, "foo.txt", "Hello, moon!")
        run(listOf("git", "add", "."), originRepo)
        run(listOf("git", "commit", "--amend", "-m", "Add one file\n\ngh-branch: add-foo"), originRepo)

        // TODO: Variant where we also have local changes here
        run(listOf("git", "fetch"), localRepo)

        val diagnosis = getDiagnosis(localRepo, options, gh)
        assertThat(normalizeHashes(diagnosis)).isEqualTo(
            Diagnosis("main", listOf(
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
            ))
        )
        assertThat(getActionToTake(diagnosis, options)).isEqualTo(ActionPlan.ReconcileCommits)
    }

    @Test
    fun testBranchModifiedUpstreamWithForce() {
        val options = fromArgs("--force")
        writeFile(localRepo, "foo.txt", "Hello, world!")
        run(listOf("git", "add", "."), localRepo)
        run(listOf("git", "commit", "-m", "Add one file\n\ngh-branch: add-foo"), localRepo)

        val firstDiagnosis = getDiagnosis(localRepo, options, gh)
        pushAndManagePrs(firstDiagnosis, localRepo, gh)

        // Amend the commit upstream
        run(listOf("git", "checkout", "add-foo"), originRepo)
        writeFile(originRepo, "foo.txt", "Hello, moon!")
        run(listOf("git", "add", "."), originRepo)
        run(listOf("git", "commit", "--amend", "-m", "Add one file\n\ngh-branch: add-foo"), originRepo)

        // TODO: Variant where we also have local changes here
        run(listOf("git", "fetch"), localRepo)

        val diagnosis = getDiagnosis(localRepo, options, gh)
        assertThat(normalizeHashes(diagnosis)).isEqualTo(
            Diagnosis("main", listOf(
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
            ))
        )
        assertThat(getActionToTake(diagnosis, options)).isEqualTo(ActionPlan.ReadyToPush)
    }

    private fun normalizeHashes(diagnosis: Diagnosis): Diagnosis {
        val hashesMap = HashMap<String, String>()
        return Diagnosis(diagnosis.targetBranch, diagnosis.commits.map { normalizeHashes(it, hashesMap) })
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