package eu.ellerotta.tdkotlinify

import java.io.File

data class GeneratorConfig(
    val packageName: String = "org.example.tdlib",
    val baseClass: String = "TdObject",
    val outputDir: File,
)

object Generator {

    fun run(schemaFile: File, config: GeneratorConfig) {
        val schema = Parser.parse(schemaFile.readText())
	CategoryMapper.buildIndex(schema.types.map { it.returnType })
        val groups: Map<String, List<TlConstructor>> = schema.types
            .groupBy { it.returnType }
            .toSortedMap()

        config.outputDir.deleteRecursively()

        var filesWritten = 0
        var sealedCount = 0
        var standaloneCount = 0

        for ((returnType, group) in groups) {
            val category = CategoryMapper.get(returnType)
            val dir = File(config.outputDir, category).also { it.mkdirs() }

            if (group.size > 1) {
                // sealed interface — one file with all implementations nested
                val classDesc = schema.classDescriptions[returnType] ?: ""
                val code = KotlinEmitter.emitSealedFile(
                    returnType = returnType,
                    group = group,
                    classDesc = classDesc,
                    packageName = "${config.packageName}.$category",
                    baseClass = config.baseClass,
                )
                File(dir, "${returnType.cap()}.kt").writeText(code)
                sealedCount++
            } else {
                // standalone data class
                val ctor = group.first()
                val code = KotlinEmitter.emitStandaloneFile(
                    ctor = ctor,
                    packageName = "${config.packageName}.$category",
                    baseClass = config.baseClass,
                )
                File(dir, "${ctor.name.cap()}.kt").writeText(code)
                standaloneCount++
            }
            filesWritten++
        }

        val categoryStats = config.outputDir
            .listFiles { f -> f.isDirectory }
            ?.sortedBy { it.name }
            ?.associate { dir ->
                dir.name to (dir.listFiles()?.size ?: 0)
            } ?: emptyMap()

        println("""
 
 tdkotlinify built successfully
 
 output: ${config.outputDir.absolutePath}
 categories:
        """.trimIndent())
        for ((cat, count) in categoryStats) {
            println(" %-18s %d files".format("$cat/", count))
        }
        println()
    }
}

fun main(args: Array<String>) {
    println("""
    
 tdkotlinify
     - op td -> kotlin converter
     
    """.trimIndent())

    if (args.isEmpty() || args.first() in listOf("-h", "--help")) {
        help()
        return
    }

    val positional = mutableListOf<String>()
    var packageName = "com.example.tdlib"
    var baseClass   = "TdObject"

    var i = 0
    while (i < args.size) {
        when (args[i]) {
            "--package", "-p" -> { packageName = args[++i] }
            "--base",    "-b" -> { baseClass   = args[++i] }
            else              -> positional.add(args[i])
        }
        i++
    }

    if (positional.size < 2) {
        System.err.println("err: provide <schema.tl> and <output>")
        System.err.println()
        help()
        System.exit(1)
    }

    val schemaFile = File(positional[0])
    val outputDir  = File(positional[1])

    if (!schemaFile.exists()) {
        System.err.println("schema not found: ${schemaFile.absolutePath}")
        System.exit(1)
    }

    println("schema  : ${schemaFile.absolutePath}")
    println("output  : ${outputDir.absolutePath}")
    println("package : $packageName")
    println("base    : $baseClass")
    println()

    try {
        Generator.run(
            schemaFile = schemaFile,
            config = GeneratorConfig(
                packageName = packageName,
                baseClass = baseClass,
                outputDir = outputDir,
            )
        )
    } catch (e: Exception) {
        System.err.println("failed: ${e.message}")
        e.printStackTrace()
        System.exit(1)
    }
}

private fun help() {
    println("""
  usage:
    tdkotlinify <schema.tl> <output> [options]

  arguments:
    schema.tl    path to tdlib schema file (e.g. schema.tl)
    output       output directory

  options:
    -p, --package <name>    root package (default: com.example.tdlib)
    -b, --base    <name>    base class/interface (default: TdObject)
    -h, --help              shows this screen :)

  examples:
    tdkotlinify scheme.tl ./src/generated
    tdkotlinify scheme.tl ./out --package com.example.tdlib --base TelegramObject

  output structure:
    output/
    ├── chat/
    │   ├── Chat.kt              ← standalone data class
    │   ├── ChatType.kt          ← sealed interface with all impls nested inside
    │   └── ChatEventAction.kt
    ├── messages/
    ├── updates/
    ├── users/
    └── ...
    """.trimIndent())
}
