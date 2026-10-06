package com.openmausbot.companion.core

import java.util.Locale

enum class ToolCategory {
    SHELL, READ, EDIT, SEARCH, WEB, BROWSER, COMPUTER, MEMORY, MESSAGE, PLAN, IMAGE, MAIL, CALENDAR, OTHER,
}

/** Ordered matches keep e.g. web_search out of Search and memory_write out of Edit. */
fun toolCategory(name: String): ToolCategory {
    val lower = name.lowercase(Locale.ROOT)
    for ((category, terms) in TOOL_CATEGORIES) {
        for (term in terms) if (lower.contains(term)) return category
    }
    return ToolCategory.OTHER
}

private val TOOL_CATEGORIES = listOf(
    ToolCategory.PLAN to listOf("todo", "taskcreate", "taskupdate", "tasklist", "update_plan"),
    ToolCategory.WEB to listOf("websearch", "web_search", "webfetch", "web_fetch", "fetch", "http", "url"),
    ToolCategory.BROWSER to listOf("browser", "playwright", "navigate"),
    ToolCategory.COMPUTER to listOf("computer", "screenshot", "click", "type_text", "press_key", "scroll", "desktop", "screen"),
    ToolCategory.MEMORY to listOf("memory"),
    ToolCategory.MESSAGE to listOf("ask_bot", "messaged", "message", "delegat", "send_message", "slack"),
    ToolCategory.MAIL to listOf("gmail", "mail", "email"),
    ToolCategory.CALENDAR to listOf("calendar"),
    ToolCategory.IMAGE to listOf("image", "photo"),
    ToolCategory.EDIT to listOf("edit", "write", "apply_patch", "patch", "create_file", "str_replace", "multiedit", "filechange"),
    ToolCategory.SEARCH to listOf("grep", "glob", "search", "find", "list_dir", "ls"),
    ToolCategory.READ to listOf("read", "view", "cat", "open_file"),
    ToolCategory.SHELL to listOf("shell", "bash", "terminal", "command", "exec", "powershell", "zsh"),
)
