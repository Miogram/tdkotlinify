package eu.ellerotta.tdkotlinify

object TypeResolver {

    private val primitives = mapOf(
        "int32"  to "Int",
        "int53"  to "Long",
        "int64"  to "Long",
        "double" to "Double",
        "string" to "String",
        "Bool"   to "Boolean",
        "bool"   to "Boolean",
        "bytes"  to "ByteArray",
        "true"   to "Boolean",
    )

    fun toKotlin(tlType: String, nullable: Boolean = false): String {
        val kt = resolve(tlType)
        return if (nullable) "$kt? = null" else kt
    }

    private fun resolve(tlType: String): String {
        val vecMatch = Regex("""[Vv]ector<(.+)>""").find(tlType)
        if (vecMatch != null) {
            val inner = resolve(vecMatch.groupValues[1])
            return "List<$inner>"
        }
        primitives[tlType]?.let { return it }
        return tlType.replaceFirstChar { it.uppercaseChar() }
    }

    fun isNullable(fieldDoc: String): Boolean {
        val lower = fieldDoc.lowercase()
        return "may be null" in lower || "; if not" in lower
    }
}

