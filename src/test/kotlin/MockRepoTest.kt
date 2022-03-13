import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach

abstract class MockRepoTest {
    lateinit var project: GitProject
    lateinit var mockGh: MockGh

    @BeforeEach
    fun setUpProject() {
        project = createNewGitProject()
        mockGh = MockGh()
    }

    @AfterEach
    fun tearDownProject() {
        project.deleteTempDirs()
    }
}
