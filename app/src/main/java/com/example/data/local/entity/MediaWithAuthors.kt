package com.example.data.local.entity

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

data class MediaWithAuthors(
    @Embedded val media: MediaEntity,
    @Relation(
        parentColumn = "mediaId",
        entityColumn = "personId",
        associateBy = Junction(MediaPersonCrossRef::class)
    )
    val authors: List<PersonEntity> = emptyList()
)
