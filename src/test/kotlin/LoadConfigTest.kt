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
        assertThat(config.draft).isFalse()
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

    @Test
    fun testEnableDraftConfig() {
        addConfig("ghpush.draft", "true")
        val config = loadConfig(localRepo, gh)
        assertThat(config.draft).isTrue()
    }

    @Test
    fun testEnableDraftConfigUppercase() {
        addConfig("ghpush.draft", "TRUE")
        val config = loadConfig(localRepo, gh)
        assertThat(config.draft).isTrue()
    }

    @Test
    fun testIgnoreDraftConfigOtherValues1() {
        addConfig("ghpush.draft", "false")
        val config = loadConfig(localRepo, gh)
        assertThat(config.draft).isFalse()
    }

    @Test
    fun testIgnoreDraftConfigOtherValues2() {
        addConfig("ghpush.draft", "tata")
        val config = loadConfig(localRepo, gh)
        assertThat(config.draft).isFalse()
    }
}
