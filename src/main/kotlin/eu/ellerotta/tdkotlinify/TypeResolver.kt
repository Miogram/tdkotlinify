package eu.ellerotta.tdkotlinify

object TypeResolver {

    private val primitives = mapOf(
        "int32"   to "Int",
        "int53"   to "Long",
        "int64"   to "Long",
        "double"  to "Double",
        "string"  to "String",
        "String"  to "String",
        "Bool"    to "Boolean",
        "bool"    to "Boolean",
        "bytes"   to "ByteArray",
        "true"    to "Boolean",
        "True"    to "Boolean",
        "Int32"   to "Int",
        "Int53"   to "Long",
        "Int64"   to "Long",
        "Double"  to "Double",
        // error stays as TdApi.Error - so has no domain counterpart
        "Error"   to "org.drinkless.tdlib.TdApi.Error",
        "error"   to "org.drinkless.tdlib.TdApi.Error",
    )

    fun toKotlin(tlType: String, nullable: Boolean = false): String {
        val kt = resolve(tlType)
        return if (nullable) "$kt? = null" else kt
    }

    fun isPrimitive(tlType: String): Boolean {
        val vecMatch = Regex("""[Vv]ector<(.+)>""").find(tlType)
        if (vecMatch != null) return isPrimitive(vecMatch.groupValues[1])
        return primitives.containsKey(tlType)
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
