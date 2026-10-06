package com.openmausbot.companion.core

import kotlin.test.Test
import kotlin.test.assertEquals

class ToolCategoryTest {
    @Test
    fun matchesEverySpecifiedToolSpellingCaseInsensitively() {
        val spellings = mapOf(
            ToolCategory.PLAN to listOf("todo", "TaskCreate", "TaskUpdate", "TaskList", "update_plan"),
            ToolCategory.WEB to listOf("websearch", "web_search", "webfetch", "web_fetch", "fetch", "http", "url"),
            ToolCategory.BROWSER to listOf("browser", "playwright", "navigate"),
            ToolCategory.COMPUTER to listOf("computer", "screenshot", "click", "type_text", "press_key", "scroll", "desktop", "screen"),
            ToolCategory.MEMORY to listOf("memory"),
            ToolCategory.MESSAGE to listOf("ask_bot", "messaged", "message", "delegate", "send_message", "slack"),
            ToolCategory.MAIL to listOf("gmail", "mail", "email"),
            ToolCategory.CALENDAR to listOf("calendar"),
            ToolCategory.IMAGE to listOf("image", "photo"),
            ToolCategory.EDIT to listOf("edit", "write", "apply_patch", "patch", "create_file", "str_replace", "multiedit", "filechange"),
            ToolCategory.SEARCH to listOf("grep", "glob", "search", "find", "list_dir", "ls"),
            ToolCategory.READ to listOf("read", "view", "cat", "open_file"),
            ToolCategory.SHELL to listOf("shell", "bash", "terminal", "command", "exec", "powershell", "zsh"),
        )
        for ((category, names) in spellings) for (name in names) {
            assertEquals(category, toolCategory(name), name)
            assertEquals(category, toolCategory(name.uppercase()), name)
        }
        assertEquals(ToolCategory.OTHER, toolCategory(""))
        assertEquals(ToolCategory.OTHER, toolCategory("unrecognized_tool"))
    }

    @Test
    fun firstMatchingCategoryWinsRatherThanTheLastOrMostGeneral() {
        val examples = mapOf(
            "todo_web_search" to ToolCategory.PLAN,
            "TaskCreate_write" to ToolCategory.PLAN,
            "web_search" to ToolCategory.WEB,
            "browser_fetch" to ToolCategory.WEB,
            "browser_screenshot" to ToolCategory.BROWSER,
            "computer_memory" to ToolCategory.COMPUTER,
            "memory_write" to ToolCategory.MEMORY,
            "send_message_email" to ToolCategory.MESSAGE,
            "mail_calendar" to ToolCategory.MAIL,
            "calendar_edit" to ToolCategory.CALENDAR,
            "image_read" to ToolCategory.IMAGE,
            "edit_search" to ToolCategory.EDIT,
            "grep_read" to ToolCategory.SEARCH,
            "read_shell" to ToolCategory.READ,
        )
        for ((name, category) in examples) assertEquals(category, toolCategory(name), name)
    }
}
