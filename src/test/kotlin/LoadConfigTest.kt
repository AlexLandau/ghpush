package com.github.alexlandau.ghpush

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class LoadConfigTest: MockRepoTest() {
    private fun addConfig(key: String, value: String) {
        run(listOf("git", "config", "--add", key, value), localRepo)
    }

    @Test
    fun testNoConfig() {
        val config = loadConfig(localRepo, gh)
        assertThat(config.prefix).isNull()
    }

    @Test
    fun testNormalPrefix() {
        addConfig("ghpush.prefix", "my-project")
        val config = loadConfig(localRepo, gh)
        assertThat(config.prefix).isEqualTo("my-project")
    }

    @Test
    fun testEmailPrefix() {
        addConfig("ghpush.prefix", "email")
        addConfig("user.email", "fake@example.com")
        val config = loadConfig(localRepo, gh)
        assertThat(config.prefix).isEqualTo("fake")
    }

    @Test
    fun testUsernamePrefix() {
        addConfig("ghpush.prefix", "username")
        val config = loadConfig(localRepo, gh)
        assertThat(config.prefix).isEqualTo("TestUser")
    }
}
