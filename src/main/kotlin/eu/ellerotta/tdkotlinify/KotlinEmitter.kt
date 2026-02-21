package eu.ellerotta.tdkotlinify

data class GeneratedFile(
    val relativePath: String,   // e.g. "chat/Chat.kt"
    val content: String,
)

private val contextualTypes = setOf(
    "org.drinkless.tdlib.TdApi.Error",
)

object KotlinEmitter {

    private const val WRAP_AT = 100

    /**
     * emit a sealed interface file with all impls nested inside
     *
     * ```kotlin
     * sealed interface ReactionType : TdObject {
     *     data class ReactionTypeEmoji(...) : ReactionType
     *     data object ReactionTypePaid    : ReactionType
     * }
     * ```
     */
    fun emitSealedFile(
        returnType: String,
        group: List<TlConstructor>,
        classDesc: String,
        packageName: String,
        baseClass: String,
    ): String = buildString {
        appendLine("package $packageName")
        appendLine()
        appendLine("import kotlinx.serialization.Contextual")
        appendLine("import kotlinx.serialization.SerialName")
        appendLine("import kotlinx.serialization.Serializable")
        appendLine()

        val sealedName = returnType.cap()

        if (classDesc.isNotBlank()) {
            appendLine("/**")
            appendLine(" * $classDesc")
            appendLine(" */")
        }

        appendLine("@Serializable")
        appendLine("public sealed interface $sealedName : $baseClass {")

        for (ctor in group) {
            appendLine()
            append(emitClass(ctor, parentType = sealedName, indent = "    ", baseClass = baseClass))
        }

        appendLine("}")
    }
    
    /**
     * emit a standalone data class file for a single-constructor type
     */
    fun emitStandaloneFile(
        ctor: TlConstructor,
        packageName: String,
        baseClass: String,
    ): String = buildString {
        appendLine("package $packageName")
        appendLine()
        appendLine("import kotlinx.serialization.Contextual")
        appendLine("import kotlinx.serialization.SerialName")
        appendLine("import kotlinx.serialization.Serializable")
        appendLine()
        append(emitClass(ctor, parentType = null, indent = "", baseClass = baseClass))
    }
    
    private fun emitClass(
        ctor: TlConstructor,
        parentType: String?,
        indent: String,
        baseClass: String,
    ): String = buildString {
        val className = ctor.name.cap()
        val parent = if (parentType != null) parentType else baseClass

        if (ctor.description.isNotBlank() || ctor.fieldDocs.isNotEmpty()) {
            appendLine(kdoc(ctor, indent))
        }

        appendLine("${indent}@Serializable")
        appendLine("""${indent}@SerialName(value = "${ctor.name}")""")

        if (ctor.fields.isEmpty()) {
            appendLine("${indent}public data object $className : $parent")
            return@buildString
        }

        appendLine("${indent}public data class $className(")

        ctor.fields.forEachIndexed { idx, field ->
            val camel = field.snakeName.snakeToCamel()
            val doc = ctor.fieldDocs[field.snakeName] ?: ""
            val nullable = TypeResolver.isNullable(doc)
            val ktType = TypeResolver.toKotlin(field.tlType, nullable)
            val comma = if (idx < ctor.fields.lastIndex) "," else ""

            // @Contextual needed for types without a kotlinx serializer (e.g. TdApi.Error)
            val needsContextual = contextualTypes.any { ktType.contains(it) }

            appendLine("""$indent    @SerialName(value = "${field.snakeName}")""")
            if (needsContextual) appendLine("$indent    @Contextual")
            appendLine("$indent    public val $camel: $ktType$comma")
        }

        appendLine("$indent) : $parent")
    }
    
    private fun kdoc(ctor: TlConstructor, indent: String): String = buildString {
        appendLine("$indent/**")

        if (ctor.description.isNotBlank()) {
            val words = ctor.description.split(' ')
            var buf = "$indent *"
            for (word in words) {
                if (buf.length + word.length + 1 > WRAP_AT) {
                    appendLine(buf)
                    buf = "$indent * $word"
                } else {
                    buf += " $word"
                }
            }
            appendLine(buf)
        }

        val propLines = ctor.fields.mapNotNull { field ->
            val doc = ctor.fieldDocs[field.snakeName] ?: return@mapNotNull null
            "$indent * @property ${field.snakeName.snakeToCamel()} $doc"
        }

        if (propLines.isNotEmpty()) {
            if (ctor.description.isNotBlank()) appendLine("$indent *")
            propLines.forEach { appendLine(it) }
        }

        append("$indent */")
    }
}

fun String.cap() = replaceFirstChar { it.uppercaseChar() }

fun String.snakeToCamel(): String {
    val parts = split('_')
    return parts.first() + parts.drop(1).joinToString("") { it.cap() }
}

