package com.high.theone.model

data class Project(
    val id: String,
    val name: String,
    val description: String = "",
    val createdAt: Long = 0L,
    val modifiedAt: Long = 0L,
    val samplesDirectory: String = "",
    val padsDirectory: String = ""
)
