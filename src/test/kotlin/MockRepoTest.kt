import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach

abstract class MockRepoTest {
    lateinit var gitRepos: GitProject
    lateinit var gh: MockGh
    // Convenience getters
    val localRepo get() = gitRepos.localRepo
    val originRepo get() = gitRepos.originRepo

    @BeforeEach
    fun setUpProject() {
        gitRepos = createNewGitProject()
        gh = MockGh()
    }

    @AfterEach
    fun tearDownProject() {
        gitRepos.deleteTempDirs()
    }
}
