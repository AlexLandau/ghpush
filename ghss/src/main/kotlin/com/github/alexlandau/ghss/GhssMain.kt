package com.github.alexlandau.ghss

fun main() {
    try {
        println("Hello, world!")
    } catch (e: GhssException) {
        println(e.messageToUser)
        System.exit(1)
    }
}
