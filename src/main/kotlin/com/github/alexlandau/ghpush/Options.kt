package com.github.alexlandau.ghpush

data class Options(
    val force: Boolean,
    val help: Boolean,
    val onto: String?,
    val version: Boolean,

    val unrecognizedArgs: List<String>,
    val errors: List<String>,
)

fun readArgs(args: Array<out String>): Options {
    var force = false
    var help = false
    var onto: String? = null
    var version = false

    val unrecognizedArgs = ArrayList<String>()
    val errors = ArrayList<String>()

    val itr = args.iterator()
    while (itr.hasNext()) {
        val arg = itr.next()
        if (arg == "-f" || arg == "--force") {
            force = true
        } else if (arg == "-h" || arg == "--help") {
            help = true
        } else if (arg == "--onto") {
            if (itr.hasNext()) {
                onto = itr.next()
            } else {
                errors.add("The --onto flag requires an argument. Examples: '--onto main' or '--onto=main'")
            }
        } else if (arg.startsWith("--onto=")) {
            onto = arg.removePrefix("--onto=")
        } else if (arg == "-v" || arg == "--version") {
            version = true
        } else {
            unrecognizedArgs.add(arg)
        }
    }

    return Options(
        force = force,
        help = help,
        onto = onto,
        version = version,

        unrecognizedArgs = unrecognizedArgs,
        errors = errors,
    )
}

internal fun fromArgs(vararg args: String): Options {
    return readArgs(args)
}