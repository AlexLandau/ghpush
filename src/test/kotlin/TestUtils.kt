import com.github.alexlandau.ghss.exec
import com.github.alexlandau.ghss.getCommandOutput
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

data class GitProject(
    /**
     * The folder containing the "local" git repo. The CLI will be run against this one.
     *
     * It will have a remote configured to point to originRepo.
     */
    val localRepo: File,
    /**
     * The folder containing the "origin" git repo, imitating the one on GitHub. This can
     * be used to make "upstream" changes and test how the CLI reacts to those.
     */
    val originRepo: File,
) {
    fun deleteTempDirs() {
        localRepo.deleteRecursively()
        originRepo.deleteRecursively()
    }
}

fun createNewGitProject(): GitProject {
    val testDirsRoot = Path.of("tests-tmp")
    Files.createDirectories(testDirsRoot)
    val originDir = Files.createTempDirectory(testDirsRoot, "origin").toFile()
    getCommandOutput(listOf("git", "init"), originDir)
    getCommandOutput(listOf("git", "checkout", "-b", "develop"), originDir)
    getCommandOutput(listOf("git", "commit", "--allow-empty", "-m", "Initial commit"), originDir)

    val localDir = Files.createTempDirectory(testDirsRoot, "local").toFile()
    getCommandOutput(listOf("git", "clone", originDir.path, localDir.path), File("."))
    return GitProject(localDir, originDir)
}

fun writeFile(repoPath: File, filePath: String, text: String) {
    File(repoPath, filePath).writeText(text)
}
