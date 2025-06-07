package com.example.theone.commands

interface Command {
    fun execute()
    fun undo()
    // Optional: fun canUndo(): Boolean = true
    // Optional: val description: String // For UI history
}
