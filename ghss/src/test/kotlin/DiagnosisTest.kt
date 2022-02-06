import com.github.alexlandau.ghss.CommitDiagnosis
import com.github.alexlandau.ghss.Diagnosis
import com.github.alexlandau.ghss.getCommandOutput
import com.github.alexlandau.ghss.getDiagnosis
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

// TODO: Consider a getCommandOutput alternative (should also throw when exit code != 0)
class DiagnosisTest {
    @Test
    fun testDiagnosisEmptyBranch() {
        val gitPaths = createNewGitProject()

        val diagnosis = getDiagnosis(gitPaths.localRepo)
        assertEquals(Diagnosis(listOf()), diagnosis)

        gitPaths.deleteTempDirs()
    }

    @Test
    fun testDiagnosisSingleCommit() {
        val gitPath = createNewGitProject()
        writeFile(gitPath.localRepo, "foo.txt", "Hello, world!")
        getCommandOutput(listOf("git", "add", "."), gitPath.localRepo)
        getCommandOutput(listOf("git", "commit", "-m", "Add one file"), gitPath.localRepo)

        val diagnosis = getDiagnosis(gitPath.localRepo)
        assertEquals(
            Diagnosis(listOf(
                CommitDiagnosis(
                    fullHash = "1",
                    shortHash = "2",
                    title = "Add one file",
                    ghBranchTag = null,
                    remoteHash = null,
                    prNumber = null,
                    prStatus = null
                )
            )),
            normalizeHashes(diagnosis)
        )

        gitPath.deleteTempDirs()
    }

    @Test
    fun testDiagnosisMultipleNewCommits() {
        val gitPath = createNewGitProject()
        writeFile(gitPath.localRepo, "foo.txt", "Hello, Bob.\n")
        getCommandOutput(listOf("git", "add", "."), gitPath.localRepo)
        getCommandOutput(listOf("git", "commit", "-m", "Add one file"), gitPath.localRepo)

        writeFile(gitPath.localRepo, "foo.txt", "Hello, Bob.\nHello, Alice!\n")
        getCommandOutput(listOf("git", "add", "."), gitPath.localRepo)
        getCommandOutput(listOf("git", "commit", "-m", "Edit the file"), gitPath.localRepo)

        writeFile(gitPath.localRepo, "bar.txt", "Buffalo buffalo Buffalo buffalo buffalo buffalo Buffalo buffalo.")
        getCommandOutput(listOf("git", "add", "."), gitPath.localRepo)
        getCommandOutput(listOf("git", "commit", "-m", "Add a completely valid sentence"), gitPath.localRepo)

        val diagnosis = getDiagnosis(gitPath.localRepo)
        assertEquals(
            Diagnosis(listOf(
                CommitDiagnosis(
                    fullHash = "1",
                    shortHash = "2",
                    title = "Add one file",
                    ghBranchTag = null,
                    remoteHash = null,
                    prNumber = null,
                    prStatus = null,
                ),
                CommitDiagnosis (
                    fullHash = "3",
                    shortHash = "4",
                    title = "Edit the file",
                    ghBranchTag = null,
                    remoteHash = null,
                    prNumber = null,
                    prStatus = null,
                ),
                CommitDiagnosis (
                    fullHash = "5",
                    shortHash = "6",
                    title = "Add a completely valid sentence",
                    ghBranchTag = null,
                    remoteHash = null,
                    prNumber = null,
                    prStatus = null,
                ),
            )),
            normalizeHashes(diagnosis)
        )

        gitPath.deleteTempDirs()
    }

    @Test
    fun testDiagnosisNewCommitWithGhBranch() {
        val gitPath = createNewGitProject()
        writeFile(gitPath.localRepo, "foo.txt", "Hello, world!")
        getCommandOutput(listOf("git", "add", "."), gitPath.localRepo)
        getCommandOutput(listOf("git", "commit", "-m", "Add one file\n\ngh-branch: add-foo"), gitPath.localRepo)

        val diagnosis = getDiagnosis(gitPath.localRepo)
        assertEquals(
            Diagnosis(listOf(
                CommitDiagnosis(
                    fullHash = "1",
                    shortHash = "2",
                    title = "Add one file",
                    ghBranchTag = "add-foo",
                    remoteHash = null,
                    prNumber = null,
                    prStatus = null
                )
            )),
            normalizeHashes(diagnosis)
        )

        gitPath.deleteTempDirs()
    }

    @Test
    fun testDiagnosisExistingUnchangedCommit() {
        val gitPath = createNewGitProject()
        writeFile(gitPath.localRepo, "foo.txt", "Hello, world!")
        getCommandOutput(listOf("git", "add", "."), gitPath.localRepo)
        getCommandOutput(listOf("git", "commit", "-m", "Add one file\n\ngh-branch: add-foo"), gitPath.localRepo)

        getCommandOutput(listOf("git", "push", "origin", "HEAD:add-foo"), gitPath.localRepo)

        val diagnosis = getDiagnosis(gitPath.localRepo)
        assertEquals(
            Diagnosis(listOf(
                CommitDiagnosis(
                    fullHash = "1",
                    shortHash = "2",
                    title = "Add one file",
                    ghBranchTag = "add-foo",
                    remoteHash = "1",
                    prNumber = null,
                    prStatus = null
                )
            )),
            normalizeHashes(diagnosis)
        )

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
        return commit.copy(
            fullHash = fullHash,
            shortHash = shortHash,
            remoteHash = remoteHash
        )
    }

    private fun normalizeHash(hash: String, hashesMap: MutableMap<String, String>): String {
        return hashesMap.computeIfAbsent(hash, { input -> (hashesMap.size + 1).toString() })
    }
}