import com.github.alexlandau.ghss.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

// TODO: Consider a getCommandOutput alternative (should also throw when exit code != 0)
class DiagnosisTest {
    @Test
    fun testDiagnosisEmptyBranch() {
        val gitPaths = createNewGitProject()
        val gh = MockGh()

        val diagnosis = getDiagnosis(gitPaths.localRepo, gh)
        assertEquals(Diagnosis(listOf()), diagnosis)
        assertEquals(ActionPlan.NothingToPush, getActionToTake(diagnosis))

        gitPaths.deleteTempDirs()
    }

    @Test
    fun testDiagnosisSingleCommit() {
        val gitPath = createNewGitProject()
        val gh = MockGh()
        writeFile(gitPath.localRepo, "foo.txt", "Hello, world!")
        getCommandOutput(listOf("git", "add", "."), gitPath.localRepo)
        getCommandOutput(listOf("git", "commit", "-m", "Add one file"), gitPath.localRepo)

        val diagnosis = getDiagnosis(gitPath.localRepo, gh)
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
        assertEquals(ActionPlan.AddBranchNames, getActionToTake(diagnosis))

        gitPath.deleteTempDirs()
    }

    @Test
    fun testDiagnosisMultipleNewCommits() {
        val gitPath = createNewGitProject()
        val gh = MockGh()
        writeFile(gitPath.localRepo, "foo.txt", "Hello, Bob.\n")
        getCommandOutput(listOf("git", "add", "."), gitPath.localRepo)
        getCommandOutput(listOf("git", "commit", "-m", "Add one file"), gitPath.localRepo)

        writeFile(gitPath.localRepo, "foo.txt", "Hello, Bob.\nHello, Alice!\n")
        getCommandOutput(listOf("git", "add", "."), gitPath.localRepo)
        getCommandOutput(listOf("git", "commit", "-m", "Edit the file"), gitPath.localRepo)

        writeFile(gitPath.localRepo, "bar.txt", "Buffalo buffalo Buffalo buffalo buffalo buffalo Buffalo buffalo.")
        getCommandOutput(listOf("git", "add", "."), gitPath.localRepo)
        getCommandOutput(listOf("git", "commit", "-m", "Add a completely valid sentence"), gitPath.localRepo)

        val diagnosis = getDiagnosis(gitPath.localRepo, gh)
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
        assertEquals(ActionPlan.AddBranchNames, getActionToTake(diagnosis))

        gitPath.deleteTempDirs()
    }

    @Test
    fun testDiagnosisNewCommitWithGhBranch() {
        val gitPath = createNewGitProject()
        val gh = MockGh()
        writeFile(gitPath.localRepo, "foo.txt", "Hello, world!")
        getCommandOutput(listOf("git", "add", "."), gitPath.localRepo)
        getCommandOutput(listOf("git", "commit", "-m", "Add one file\n\ngh-branch: add-foo"), gitPath.localRepo)

        val diagnosis = getDiagnosis(gitPath.localRepo, gh)
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
        assertEquals(ActionPlan.ReadyToPush, getActionToTake(diagnosis))

        gitPath.deleteTempDirs()
    }

    @Test
    fun testDiagnosisExistingUnchangedCommit() {
        val gitPath = createNewGitProject()
        val gh = MockGh()
        writeFile(gitPath.localRepo, "foo.txt", "Hello, world!")
        getCommandOutput(listOf("git", "add", "."), gitPath.localRepo)
        getCommandOutput(listOf("git", "commit", "-m", "Add one file\n\ngh-branch: add-foo"), gitPath.localRepo)

        getCommandOutput(listOf("git", "push", "origin", "HEAD:add-foo"), gitPath.localRepo)

        val diagnosis = getDiagnosis(gitPath.localRepo, gh)
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
        assertEquals(ActionPlan.NothingToPush, getActionToTake(diagnosis))

        gitPath.deleteTempDirs()
    }

    @Test
    fun testDiagnosisFindsPreviouslyPushedHash() {
        val gitPath = createNewGitProject()
        val gh = MockGh()
        writeFile(gitPath.localRepo, "foo.txt", "Hello, world!")
        getCommandOutput(listOf("git", "add", "."), gitPath.localRepo)
        getCommandOutput(listOf("git", "commit", "-m", "Add one file\n\ngh-branch: add-foo"), gitPath.localRepo)
        writeFile(gitPath.localRepo, "foo.txt", "Hello, world!!!")
        getCommandOutput(listOf("git", "commit", "-a", "-m", "Add another file\n\ngh-branch: add-more"), gitPath.localRepo)

        val firstDiagnosis = getDiagnosis(gitPath.localRepo, gh)
        pushAndManagePrs(firstDiagnosis, gitPath.localRepo, gh)
//        getCommandOutput(listOf("git", "push", "origin", "HEAD:add-foo"), gitPath.localRepo)

        val diagnosis = getDiagnosis(gitPath.localRepo, gh)
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
        assertEquals(ActionPlan.NothingToPush, getActionToTake(diagnosis))

        gitPath.deleteTempDirs()
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