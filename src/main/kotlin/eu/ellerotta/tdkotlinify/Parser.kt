package eu.ellerotta.tdkotlinify

data class TlField(
    val snakeName: String,
    val tlType: String,
)

data class TlConstructor(
    val name: String,          // e.g. "reactionTypeEmoji"
    val returnType: String,    // e.g. "ReactionType"
    val description: String,
    val fieldDocs: Map<String, String>,   // snakeName → doc
    val fields: List<TlField>,
    val isFunction: Boolean = false,
)

data class TlSchema(
    val types: List<TlConstructor>,
    val functions: List<TlConstructor>,
    /** returnType → @class description */
    val classDescriptions: Map<String, String>,
)

object Parser {

    fun parse(text: String): TlSchema {
        val lines = text.lines()
        val classDescriptions = mutableMapOf<String, String>()

        // extract @class descriptions first
        for (line in lines) {
            val m = Regex("""//@class\s+(\w+)\s+@description\s+(.+)""").find(line)
            if (m != null) {
                classDescriptions[m.groupValues[1]] = m.groupValues[2].trim()
            }
        }

        // split into sections
        val types = mutableListOf<TlConstructor>()
        val functions = mutableListOf<TlConstructor>()

        var isFunction = false
        var i = 0
        val commentBuf = mutableListOf<String>()

        while (i < lines.size) {
            val raw = lines[i]
            val trimmed = raw.trim()

            when {
                trimmed == "---functions---" -> { isFunction = true;  commentBuf.clear(); i++; continue }
                trimmed == "---types---"     -> { isFunction = false; commentBuf.clear(); i++; continue }
                trimmed.isEmpty()            -> { i++; continue }
                trimmed.startsWith("//")     -> { commentBuf.add(trimmed); i++; continue }
            }

            // multiline definition until ;
            val defnLines = mutableListOf<String>()
            while (i < lines.size) {
                val l = lines[i].trim()
                defnLines.add(l)
                i++
                if (l.endsWith(";")) break
            }

            val fullDefn = defnLines.joinToString(" ")
            val ctor = parseConstructor(fullDefn, commentBuf.toList(), isFunction)
            if (ctor != null) {
                if (isFunction) functions.add(ctor) else types.add(ctor)
            }
            commentBuf.clear()
        }

        return TlSchema(types, functions, classDescriptions)
    }

    private fun parseConstructor(
        fullDefn: String,
        comments: List<String>,
        isFunction: Boolean,
    ): TlConstructor? {
        val eqIdx = fullDefn.lastIndexOf('=')
        if (eqIdx < 0) return null

        val returnType = fullDefn.substring(eqIdx + 1)
            .trim().trimEnd(';').trim()
            .replaceFirstChar { it.uppercaseChar() }

        val lhs = fullDefn.substring(0, eqIdx).trim()
        val nameMatch = Regex("""^([a-zA-Z_][a-zA-Z0-9_.]*)""").find(lhs) ?: return null
        val name = nameMatch.value

        val fieldsStr = lhs.removePrefix(name).trim()

        // skip true primitives (e.g. "int32 = Int32;") no comments no fields
        val (description, fieldDocs) = parseComments(comments)
        if (fieldsStr.isEmpty() && description.isEmpty() && comments.isEmpty()) return null

        val fields = parseFields(fieldsStr)

        return TlConstructor(
            name = name,
            returnType = returnType,
            description = description,
            fieldDocs = fieldDocs,
            fields = fields,
            isFunction = isFunction,
        )
    }

    fun parseComments(comments: List<String>): Pair<String, Map<String, String>> {
        val joined = comments.joinToString(" ") { it.trimStart('/').trim() }
        // split on @token — produces [prefix, key1, val1, key2, val2, ...]
        val tokens = joined.split(Regex("""@(\w+)"""))
        val keys = Regex("""@(\w+)""").findAll(joined).map { it.groupValues[1] }.toList()

        var description = ""
        val fieldDocs = mutableMapOf<String, String>()

        tokens.forEachIndexed { idx, value ->
            val trimmed = value.trim()
            if (idx == 0) return@forEachIndexed  // before first @
            val key = keys.getOrNull(idx - 1) ?: return@forEachIndexed
            when (key) {
                "description" -> description = trimmed
                "class"       -> { /* skip */ }
                else          -> fieldDocs[key] = trimmed
            }
        }
        return description to fieldDocs
    }

    private fun parseFields(fieldsStr: String): List<TlField> {
        // match: type pairs, skip flags (#) and conditional flags (flags.N?true)
        return Regex("""(\w+):([\w.<>]+)""")
            .findAll(fieldsStr)
            .map { m -> TlField(m.groupValues[1], m.groupValues[2]) }
            .filterNot { it.snakeName == "flags" }          // flags:# — metadata
            .toList()
    }
}
