import com.github.alexlandau.ghss.getCommandOutput
import com.github.alexlandau.ghss.loadConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class LoadConfigTest: MockRepoTest() {
    private fun addConfig(key: String, value: String) {
        getCommandOutput(listOf("git", "config", "--add", key, value), project.localRepo)
    }

    @Test
    fun testNoConfig() {
        val config = loadConfig(project.localRepo, mockGh)
        assertThat(config.prefix).isNull()
    }

    @Test
    fun testNormalPrefix() {
        addConfig("ghpush.prefix", "my-project")
        val config = loadConfig(project.localRepo, mockGh)
        assertThat(config.prefix).isEqualTo("my-project")
    }

    @Test
    fun testEmailPrefix() {
        addConfig("ghpush.prefix", "email")
        addConfig("user.email", "fake@example.com")
        val config = loadConfig(project.localRepo, mockGh)
        assertThat(config.prefix).isEqualTo("fake")
    }

    @Test
    fun testUsernamePrefix() {
        addConfig("ghpush.prefix", "username")
        val config = loadConfig(project.localRepo, mockGh)
        assertThat(config.prefix).isEqualTo("TestUser")
    }
}
