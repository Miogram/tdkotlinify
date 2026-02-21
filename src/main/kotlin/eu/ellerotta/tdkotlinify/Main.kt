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
        val groups: Map<String, List<TlConstructor>> = schema.types
            .groupBy { it.returnType }
            .toSortedMap()

        val domainDir = File(config.outputDir, "domain").also { it.mkdirs() }
        val mappingDir = File(config.outputDir, "mapping").also { it.mkdirs() }

        val domainPackage = "${config.packageName}.domain"
        val mappingPackage = "${config.packageName}.mapping"

        var filesWritten = 0

        for ((returnType, group) in groups) {
            if (group.size > 1) {
                val classDesc = schema.classDescriptions[returnType] ?: ""

                // domain
                File(domainDir, "${returnType.cap()}.kt").writeText(
                    KotlinEmitter.emitSealedFile(
                        returnType = returnType,
                        group = group,
                        classDesc = classDesc,
                        packageName = domainPackage,
                        baseClass = config.baseClass,
                    )
                )

                // mapping
                File(mappingDir, "${returnType.cap()}Mapping.kt").writeText(
                    MappingEmitter.emitSealedMapper(
                        returnType = returnType,
                        group = group,
                        domainPackage = domainPackage,
                        mappingPackage = mappingPackage,
                    )
                )
            } else {
                val ctor = group.first()

                // domain
                File(domainDir, "${ctor.name.cap()}.kt").writeText(
                    KotlinEmitter.emitStandaloneFile(
                        ctor = ctor,
                        packageName = domainPackage,
                        baseClass = config.baseClass,
                    )
                )

                // mapping
                File(mappingDir, "${ctor.name.cap()}Mapping.kt").writeText(
                    MappingEmitter.emitMapper(
                        ctor = ctor,
                        domainPackage = domainPackage,
                        mappingPackage = mappingPackage,
                    )
                )
            }
            filesWritten++
        }

        println("""
tdkotlinify built successfully
output : ${config.outputDir.absolutePath}
domain : $domainPackage (${domainDir.listFiles()?.size ?: 0} files)
mapping: $mappingPackage (${mappingDir.listFiles()?.size ?: 0} files)
total  : $filesWritten types
        """.trimIndent())
    }
}

fun main(args: Array<String>) {
    println("""
    
 tdkotlinify
     - overpowered td -> kotlin converter
     
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

  example:
    tdkotlinify scheme.tl ./src/generated --package eu.ellerotta.td --base TelegramObject

  output structure:
    output/
    ├── domain/
    │   ├── Chat.kt
    │   ├── ChatType.kt
    │   └── ...
    └── mapping/
        ├── ChatMapping.kt
        ├── ChatTypeMapping.kt
        └── ...
    """.trimIndent())
}

