package com.high.theone.commands

import java.util.Stack

class UndoRedoManager {
    private val undoStack: Stack<Command> = Stack()
    private val redoStack: Stack<Command> = Stack()

    fun executeCommand(command: Command) {
        command.execute()
        undoStack.push(command)
        redoStack.clear() // Clear redo stack whenever a new command is executed
        // Optional: Notify observers that command history changed
    }

    fun undo() {
        if (undoStack.isNotEmpty()) {
            val command = undoStack.pop()
            command.undo()
            redoStack.push(command)
            // Optional: Notify observers
        }
    }

    fun redo() {
        if (redoStack.isNotEmpty()) {
            val command = redoStack.pop()
            command.execute() // Re-execute the command
            undoStack.push(command)
            // Optional: Notify observers
        }
    }

    fun canUndo(): Boolean = undoStack.isNotEmpty()
    fun canRedo(): Boolean = redoStack.isNotEmpty()

    fun clearHistory() {
        undoStack.clear()
        redoStack.clear()
        // Optional: Notify observers
    }
}
