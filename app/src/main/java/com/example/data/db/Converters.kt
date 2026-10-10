package com.example.data.db

import androidx.room.TypeConverter

/**
 * Conversores de tipo para Room.
 *
 * Room no sabe guardar una List<String> en SQLite por si solo: hay que decirle
 * como convertirla a texto y como reconstruirla. Se usa como separador la coma,
 * igual que hace GROUP_CONCAT en la migracion 5 -> 6, para que los valores
 * escritos por la migracion se lean correctamente.
 */
class Converters {

    @TypeConverter
    fun fromStringList(value: List<String>?): String =
        value?.filter { it.isNotBlank() }?.joinToString(SEPARATOR) ?: ""

    @TypeConverter
    fun toStringList(value: String?): List<String> =
        value?.split(SEPARATOR)
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?: emptyList()

    private companion object {
        const val SEPARATOR = ","
    }
}
