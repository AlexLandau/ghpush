package com.github.alexlandau.ghpush

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
    run(listOf("git", "init"), originDir)
    run(listOf("git", "checkout", "-b", "main"), originDir)
    run(listOf("git", "config", "--add", "user.name", "Origin Maintainer"), originDir)
    run(listOf("git", "config", "--add", "user.email", "origin@example.com"), originDir)
    run(listOf("git", "commit", "--allow-empty", "-m", "Initial commit"), originDir)

    val localDir = Files.createTempDirectory(testDirsRoot, "local").toFile()
    run(listOf("git", "clone", originDir.path, localDir.path), File("."))
    run(listOf("git", "config", "--add", "user.name", "Test User"), localDir)
    run(listOf("git", "config", "--add", "user.email", "test@example.com"), localDir)
    return GitProject(localDir, originDir)
}

fun writeFile(repoPath: File, filePath: String, text: String) {
    File(repoPath, filePath).writeText(text)
}
