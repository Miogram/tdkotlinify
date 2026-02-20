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

object CategoryMapper {
    private const val MIN_FILES_FOR_OWN_FOLDER = 8

   private val anchors = mapOf(
        "Thumbnail"      to "media",
        "Minithumbnail"  to "media",
        "Audio"          to "media",
        "Voice"          to "media",
        "Animation"      to "media",
        
        "Authentication" to "account",
        "Authorization"  to "account",
        "PhoneNumber"    to "account",
        "EmailAddress"   to "account",
        "Passkey"        to "account",
        "Recovery"       to "account",
        "Password"       to "account",
        "Session"        to "account",
        
        "Supergroup"     to "chat",
        "BasicGroup"     to "chat",
        "SecretChat"     to "chat",
        "Boost"          to "chat",
        "Folder"         to "chat",
        "Member"         to "chat",
        
        "Profile"        to "user",
        "Accent"         to "user",
        
        "Link"           to "link",
        
        "Subscription"   to "star",
        "Transaction"    to "star",
        "Ton"            to "ton",
        
        "Network"        to "statistic",
        "Storage"        to "statistic",
        "Statistical"    to "statistic",
        
        "Revenue"        to "revenue",
        
        "Payment"        to "payment",
        "Order"          to "payment",
        "Store"          to "payment",
        
        "Premium"        to "premium",
        
        "Resale"         to "gift",
        
        "Giveaway"       to "giveaway",
        "Participant"    to "group",
        
        "Affiliate"      to "affiliate",
        
        "Sponsored"      to "sponsored",
        
        "Discard"        to "call",
        "Problem"        to "call",
        
        "Search"         to "message",
        
        "Log"            to "misc",
        "Proxy"          to "misc",
        "Address"        to "misc",
        "Connection"     to "misc",
        "Json"           to "misc",
        "Poll"           to "misc",
        "Report"         to "misc",
        "Time"           to "misc",
        "Checklist"      to "misc",
        "Country"        to "misc",
        "Game"           to "misc",
        "Language"       to "misc",
        "Location"       to "misc",
        "Price"          to "misc",
        "Personal"       to "document",
        "Target"         to "misc",
        "Option"         to "misc",
        "Sort"           to "misc",
    )

    private val stopWords = setOf(
        "Type", "Info", "Id", "State", "Result", "Settings", "Status",
        "Action", "Added", "Source", "List", "Full", "Base", "Data", "Options",
        "Parameters", "Content", "Mode", "Scope", "Value", "Object",
        "Request", "Response", "Error", "Count", "Size",
        "Get", "Set", "Add", "Can", "Has", "Is", "New", "Old",
        "Found", "Saved", "Active", "Current", "Default", "Available",
        "Input", "Output", "Sent", "Received", "Created", "Updated",
        "Upgraded", "Imported", "Connected", "Prepared", "Suggested",
        "Accepted", "Failed", "Closed", "Direct", "Public", "Shared",
        "Internal", "Quick", "Main", "Auto", "Top", "Trending", "Built",
        "Formatted", "Encrypted", "Downloaded", "Labeled", "Remote", "Local",
        "Recommended", "Unread", "Temporary", "Unconfirmed", "Validated",
        "Ok", "Test", "Vector", "String", "Double", "Seconds", "Point", "Count",
    )

    private val categoryIndex = mutableMapOf<String, MutableSet<String>>()
    private val wordToCategory = mutableMapOf<String, String>()

    fun buildIndex(allTypeNames: Collection<String>) {
        val wordFreq = mutableMapOf<String, Int>()
        for (name in allTypeNames) {
            for (word in splitCamel(name)) {
                wordFreq[word] = (wordFreq[word] ?: 0) + 1
            }
        }

        val typeToCategory = mutableMapOf<String, String>()
        for (name in allTypeNames) {
            val words = splitCamel(name)

            val cat = words
                .sortedBy { wordFreq[it] ?: Int.MAX_VALUE }
                .firstNotNullOfOrNull { anchors[it] }
                ?: words
                    .filter { (wordFreq[it] ?: 0) >= MIN_FILES_FOR_OWN_FOLDER }
                    .minByOrNull { wordFreq[it] ?: Int.MAX_VALUE }
                    ?.lowercase()
                    ?.let { normalize(it) }
                ?: words.maxByOrNull { wordFreq[it] ?: 0 }
                    ?.lowercase()
                    ?.let { normalize(it) }
                ?: "misc"

            typeToCategory[name] = cat
        }

        val catCount = mutableMapOf<String, Int>()
        for ((_, cat) in typeToCategory) {
            catCount[cat] = (catCount[cat] ?: 0) + 1
        }
        val allowed = catCount.filter { it.value >= MIN_FILES_FOR_OWN_FOLDER }.keys.toSet()

        val wordCatCount = mutableMapOf<String, MutableMap<String, Int>>()
        for ((name, cat) in typeToCategory) {
            if (cat !in allowed) continue
            for (word in splitCamel(name)) {
                wordCatCount.getOrPut(word) { mutableMapOf() }.merge(cat, 1, Int::plus)
            }
        }
        wordToCategory.clear()
        for ((word, cats) in wordCatCount) {
            wordToCategory[word] = cats.maxByOrNull { it.value }!!.key
        }

        categoryIndex.clear()
        for ((name, cat) in typeToCategory) {
            val finalCat = if (cat in allowed) {
                cat
            } else {
                splitCamel(name)
                    .sortedBy { wordFreq[it] ?: Int.MAX_VALUE }
                    .firstNotNullOfOrNull { anchors[it] ?: wordToCategory[it] }
                    ?: "misc"
            }
            categoryIndex.getOrPut(finalCat) { mutableSetOf() }.add(name)
        }
    }

    fun get(typeName: String): String {
        for ((cat, names) in categoryIndex) {
            if (typeName in names) return cat
        }
        val words = splitCamel(typeName)
        return words
            .sortedBy { wordFreq(it) }
            .firstNotNullOfOrNull { anchors[it] ?: wordToCategory[it] }
            ?: "misc"
    }

    private fun wordFreq(word: String): Int = wordToCategory[word]?.let { 0 } ?: Int.MAX_VALUE

    private fun splitCamel(name: String): List<String> =
        Regex("[A-Z][a-z0-9]*")
            .findAll(name)
            .map { it.value }
            .filter { it !in stopWords }
            .toList()

    private fun normalize(word: String): String = when {
        word.endsWith("ies") && word.length > 4 -> word.dropLast(3) + "y"
        word.endsWith("s") && word.length > 4
                && !word.endsWith("ss")
                && !word.endsWith("us")
                && !word.endsWith("is") -> word.dropLast(1)
        else -> word
    }
}

