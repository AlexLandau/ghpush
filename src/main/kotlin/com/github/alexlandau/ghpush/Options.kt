package com.github.alexlandau.ghpush

data class Options(
    val action: Action,
    val force: Boolean,
    val help: Boolean,
    val onto: String?,
    val version: Boolean,

    val unrecognizedArgs: List<String>,
    val errors: List<String>,
)

sealed class Action {
    object Push: Action()
    object Gc: Action()
}

fun readArgs(args: Array<out String>): Options {
    var action: Action? = null // null implies Push
    var force = false
    var help = false
    var onto: String? = null
    var version = false

    val unrecognizedArgs = ArrayList<String>()
    val errors = ArrayList<String>()

    val itr = args.iterator()
    while (itr.hasNext()) {
        val arg = itr.next()
        if (!arg.startsWith("-")) {
            when (action) {
                Action.Push -> throw GhpushException("Error: Cannot specify multiple commands ('$action' and '$arg')")
                Action.Gc -> throw GhpushException("Error: Cannot specify multiple commands ('$action' and '$arg')")
                null -> {
                    when (arg) {
                        "push" -> action = Action.Push
                        "gc" -> action = Action.Gc
                        else -> throw GhpushException("Error: Unknown command '$arg'")
                    }
                }
            }
        } else if (arg == "-f" || arg == "--force") {
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
        action = action ?: Action.Push,
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