package com.github.alexlandau.ghss

class GhssException(
    val messageToUser: String,
    cause: Throwable? = null,
    val additionalInfo: String? = null,
) : RuntimeException(messageToUser + (additionalInfo ?: ""), cause)