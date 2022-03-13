package com.github.alexlandau.ghpush

class GhpushException(
    val messageToUser: String,
    cause: Throwable? = null,
    val additionalInfo: String? = null,
) : RuntimeException(messageToUser + (additionalInfo ?: ""), cause)
