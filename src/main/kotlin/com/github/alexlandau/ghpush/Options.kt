package com.github.alexlandau.ghpush

data class Options(
    val force: Boolean,
    val help: Boolean,
    val version: Boolean,
    val unrecognizedArgs: List<String>,
)

fun readArgs(args: Array<out String>): Options {
    var force = false
    var help = false
    var version = false

    val unrecognizedArgs = ArrayList<String>()

    val itr = args.iterator()
    while (itr.hasNext()) {
        val arg = itr.next()
        if (arg == "-f" || arg == "--force") {
            force = true
        } else if (arg == "-h" || arg == "--help") {
            help = true
        } else if (arg == "-v" || arg == "--version") {
            version = true
        } else {
            unrecognizedArgs.add(arg)
        }
    }

    return Options(
        force = force,
        help = help,
        version = version,
        unrecognizedArgs = unrecognizedArgs,
    )
}

internal fun fromArgs(vararg args: String): Options {
    return readArgs(args)
}