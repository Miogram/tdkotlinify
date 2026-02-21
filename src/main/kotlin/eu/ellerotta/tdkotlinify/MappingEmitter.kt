package eu.ellerotta.tdkotlinify

object MappingEmitter {

    private val primitiveKtTypes = setOf(
        "Int", "Long", "Double", "Boolean", "String", "ByteArray"
    )

    /**
     * generates a mapper file for a standalone data class:
     *
     * ```kotlin
     * fun TdApi.Chat.toModel() = Chat(
     *     id = id,
     *     title = title,
     *     type = type.toModel(),
     *     ...
     * )
     * ```
     */
    fun emitMapper(
        ctor: TlConstructor,
        domainPackage: String,
        mappingPackage: String,
    ): String = buildString {
        appendLine("package $mappingPackage")
        appendLine()
        appendLine("import org.drinkless.tdlib.TdApi")
        appendLine("import $domainPackage.${ctor.name.cap()}")
        appendLine()
        append(mapperFun(ctor, receiverPrefix = "TdApi."))
    }

    /**
     * generates a mapper file for a sealed interface group:
     *
     * ```kotlin
     * fun TdApi.ChatType.toModel(): ChatType = when (this) {
     *     is TdApi.ChatTypePrivate    -> ChatTypePrivate(userId = userId)
     *     is TdApi.ChatTypeSupergroup -> ChatTypeSupergroup(...)
     *     else -> error("Unknown ChatType: $this")
     * }
     * ```
     */
    fun emitSealedMapper(
        returnType: String,
        group: List<TlConstructor>,
        domainPackage: String,
        mappingPackage: String,
    ): String = buildString {
        val sealedName = returnType.cap()

        appendLine("package $mappingPackage")
        appendLine()
        appendLine("import org.drinkless.tdlib.TdApi")
        appendLine("import $domainPackage.$sealedName")
        group.forEach { appendLine("import $domainPackage.${it.name.cap()}") }
        appendLine()

        appendLine("fun TdApi.$sealedName.toModel(): $sealedName = when (this) {")

        for (ctor in group) {
            val className = ctor.name.cap()
            if (ctor.fields.isEmpty()) {
                appendLine("    is TdApi.$className -> $className")
            } else {
                appendLine("    is TdApi.$className -> $className(")
                ctor.fields.forEachIndexed { idx, field ->
                    val camel = field.snakeName.snakeToCamel()
                    val ktType = TypeResolver.toKotlin(field.tlType).trimEnd('?')
                    val comma = if (idx < ctor.fields.lastIndex) "," else ""
                    val value = mapFieldValue(camel, field.tlType, ktType)
                    appendLine("        $camel = $value$comma")
                }
                appendLine("    )")
            }
        }

        appendLine("    else -> error(\"Unknown $sealedName: \$this\")")
        appendLine("}")
    }

    private fun mapperFun(ctor: TlConstructor, receiverPrefix: String): String = buildString {
        val className = ctor.name.cap()

        if (ctor.fields.isEmpty()) {
            appendLine("fun $receiverPrefix$className.toModel() = $className")
            return@buildString
        }

        appendLine("fun $receiverPrefix$className.toModel() = $className(")
        ctor.fields.forEachIndexed { idx, field ->
            val camel = field.snakeName.snakeToCamel()
            val ktType = TypeResolver.toKotlin(field.tlType).trimEnd('?')
            val comma = if (idx < ctor.fields.lastIndex) "," else ""
            val value = mapFieldValue(camel, field.tlType, ktType)
            appendLine("    $camel = $value$comma")
        }
        append(")")
    }

    /**
     * decides how to map a single field value
     * - primitive      -> use as-is
     * - List<X>        -> map each element if x is not primitive
     * - domain type    -> call .toModel()
     */
    private fun mapFieldValue(camel: String, tlType: String, ktType: String): String {
        val vecMatch = Regex("""[Vv]ector<(.+)>""").find(tlType)
        if (vecMatch != null) {
            val innerTl = vecMatch.groupValues[1]
            val innerKt = TypeResolver.toKotlin(innerTl)
            return if (innerKt in primitiveKtTypes) {
                "$camel.toList()"
            } else {
                "$camel.map { it.toModel() }"
            }
        }

        return if (ktType in primitiveKtTypes) camel else "$camel.toModel()"
    }
}

