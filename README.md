# tdkotlinify

reads `.tl` scheme and generates:
- `sealed interface` + nested classes in one
- `data class` / `data object` for regular types
- `@Serializable` / `@SerialName` annotations (kotlinx.serialization)
- kdoc with `@property` for each field
- nullable fields by comments `; may be null`
- snake_case → camelCase

## cli options

```
usage:
  tdkotlinify <schema.tl> <output> [options]

arguments:
  schema.tl     path to tdlib schema file (e.g. schema.tl)
  output        output directory

options:
  -p, --package <name>   core package (default: com.example.tdlib)
  -b, --base    <name>   base class/interface (def: TdObject)
  -h, --help             this screen :)
```

## fast start

```bash
# 1. build
./gradlew build

# 2. run
./gradlew run --args="schema.tl ./output"

# or w/ options
./gradlew run --args="schema.tl ./output --package com.example.tdlib --base TdObject"
```

or fat-jar:
```bash
./gradlew jar
java -jar build/libs/tdkotlinify-1.0.0.jar schema.tl ./output
```

## output example

**`chat/Chat.kt`** — standalone data class:
```kotlin
/**
 * A chat. (Can be a private chat, basic group, supergroup, or secret chat)
 *
 * @property id Chat unique identifier
 * @property type Type of the chat
 * @property title Chat title
 * @property photo Chat photo; may be null
 * ...
 */
@Serializable
@SerialName(value = "chat")
public data class Chat(
    @SerialName(value = "id")
    public val id: Long,
    @SerialName(value = "type")
    public val type: ChatType,
    @SerialName(value = "title")
    public val title: String,
    @SerialName(value = "photo")
    public val photo: ChatPhotoInfo? = null,
    ...
) : TdObject()
```

**`reactions/ReactionType.kt`** — sealed interface w/ nested classes:
```kotlin
/** Describes type of message reaction */
@Serializable
public sealed interface ReactionType : TdObject {

    /** A reaction with an emoji
     * @property emoji Text representation of the reaction */
    @Serializable
    @SerialName(value = "reactionTypeEmoji")
    public data class ReactionTypeEmoji(
        @SerialName(value = "emoji")
        public val emoji: String,
    ) : ReactionType

    /** The paid reaction in a channel chat */
    @Serializable
    @SerialName(value = "reactionTypePaid")
    public data object ReactionTypePaid : ReactionType
}
```

## structure

```
tdkotlinify/
├── build.gradle.kts
├── settings.gradle.kts
├── gradlew / gradlew.bat
└── src/main/kotlin/dev/tl/codegen/
    ├── Main.kt          — cli entry point + orchestrator
    ├── Parser.kt        — .tl scheme parser
    ├── TypeResolver.kt  — tl -> kotlin type mapping w/ CategoryMapper
    └── KotlinEmitter.kt — kotlin code generator
```

