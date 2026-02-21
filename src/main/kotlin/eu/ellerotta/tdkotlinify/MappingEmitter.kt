package eu.ellerotta.tdkotlinify

object MappingEmitter {
    
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
        append(mapperFun(ctor))
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
        appendLine()

        appendLine("fun TdApi.$sealedName.toModel(): $sealedName = when (this) {")

        for (ctor in group) {
            val className = ctor.name.cap()
            val qualifiedName = "$sealedName.$className"

            if (ctor.fields.isEmpty()) {
                appendLine("    is TdApi.$className -> $qualifiedName")
            } else {
                appendLine("    is TdApi.$className -> $qualifiedName(")
                ctor.fields.forEachIndexed { idx, field ->
                    val camel = field.snakeName.snakeToCamel()
                    val comma = if (idx < ctor.fields.lastIndex) "," else ""
                    val doc = ctor.fieldDocs[field.snakeName] ?: ""
                    val nullable = TypeResolver.isNullable(doc)
                    val value = mapFieldValue(camel, field.tlType, nullable)
                    appendLine("        $camel = $value$comma")
                }
                appendLine("    )")
            }
        }

        appendLine("    else -> error(\"Unknown $sealedName: \$this\")")
        append("}")
    }

    private fun mapperFun(ctor: TlConstructor): String = buildString {
        val className = ctor.name.cap()

        if (ctor.fields.isEmpty()) {
            appendLine("fun TdApi.$className.toModel() = $className")
            return@buildString
        }

        appendLine("fun TdApi.$className.toModel() = $className(")
        ctor.fields.forEachIndexed { idx, field ->
            val camel = field.snakeName.snakeToCamel()
            val comma = if (idx < ctor.fields.lastIndex) "," else ""
            val doc = ctor.fieldDocs[field.snakeName] ?: ""
            val nullable = TypeResolver.isNullable(doc)
            val value = mapFieldValue(camel, field.tlType, nullable)
            appendLine("    $camel = $value$comma")
        }
        append(")")
    }

    /**
     * decides how to map a single field value
     * - primitive          -> use as-is
     * - List<primitive>    -> .toList()
     * - List<domain>       -> .map { it.toModel() }
     * - nullable domain    -> ?.toModel()
     * - non-null domain    -> .toModel()
     */
    private fun mapFieldValue(camel: String, tlType: String, nullable: Boolean): String {
        val vecMatch = Regex("""[Vv]ector<(.+)>""").find(tlType)
        if (vecMatch != null) {
            val innerTl = vecMatch.groupValues[1]
            // nested vector: vector<vector<X>>
            val innerVecMatch = Regex("""[Vv]ector<(.+)>""").find(innerTl)
            if (innerVecMatch != null) {
                val innerInnerTl = innerVecMatch.groupValues[1]
                return if (TypeResolver.isPrimitive(innerInnerTl))
                    "$camel.map { it.toList() }"
                else
                    "$camel.map { row -> row.map { it.toModel() } }"
            }
            return if (TypeResolver.isPrimitive(innerTl)) "$camel.toList()"
            else "$camel.map { it.toModel() }"
        }
        if (TypeResolver.isPrimitive(tlType)) return camel
        return if (nullable) "$camel?.toModel()" else "$camel.toModel()"
    }
}

