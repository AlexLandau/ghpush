package com.github.alexlandau.ghpush

class GhssException(
    val messageToUser: String,
    cause: Throwable? = null,
    val additionalInfo: String? = null,
) : RuntimeException(messageToUser + (additionalInfo ?: ""), cause)
