package pbandk.gen

import pbandk.Util
import pbandk.wkt.DescriptorProto
import pbandk.wkt.EnumDescriptorProto
import pbandk.wkt.FieldDescriptorProto
import pbandk.wkt.FileDescriptorProto

open class FileBuilder(val namer: Namer = Namer.Standard, val supportMaps: Boolean = true) {
    fun buildFile(ctx: Context) = File(
        name = ctx.fileDesc.name!!,
        packageName = ctx.fileDesc.`package`?.takeIf { it.isNotEmpty() },
        kotlinPackageName = packageName(ctx),
        version = ctx.fileDesc.syntax?.removePrefix("proto")?.toIntOrNull() ?: 2,
        types = typesFromProto(ctx, ctx.fileDesc.enumType, ctx.fileDesc.messageType)
    )

    protected fun packageName(ctx: Context) =
        ctx.params["kotlin_package"] ?: ctx.fileDesc.options?.uninterpretedOption?.find {
            it.name.singleOrNull()?.namePart == "kotlin_package"
        }?.stringValue?.array?.let(Util::utf8ToString) ?: ctx.fileDesc.`package`?.takeIf { it.isNotEmpty() }

    protected fun typesFromProto(
        ctx: Context,
        enumTypes: List<EnumDescriptorProto>,
        msgTypes: List<DescriptorProto>
    ) = mutableSetOf<String>().let { usedTypeNames ->
        enumTypes.map { fromProto(it, usedTypeNames) } + msgTypes.map { fromProto(ctx, it, usedTypeNames) }
    }

    protected fun fromProto(enumDesc: EnumDescriptorProto, usedTypeNames: MutableSet<String>) = File.Type.Enum(
        name = enumDesc.name!!,
        values = mutableSetOf<String>().let { usedValueNames ->
            enumDesc.value.map {
                File.Type.Enum.Value(it.number!!, it.name!!, namer.newEnumValueName(it.name!!, usedValueNames))
            }
        },
        kotlinTypeName = namer.newTypeName(enumDesc.name!!, usedTypeNames)
    )

    protected fun fromProto(
        ctx: Context,
        msgDesc: DescriptorProto,
        usedTypeNames: MutableSet<String>
    ): File.Type.Message = File.Type.Message(
        name = msgDesc.name!!,
        fields = fieldsFromProto(ctx, msgDesc, usedTypeNames),
        nestedTypes = typesFromProto(ctx, msgDesc.enumType, msgDesc.nestedType),
        mapEntry = supportMaps && msgDesc.options?.mapEntry == true,
        kotlinTypeName = namer.newTypeName(msgDesc.name!!, usedTypeNames),
        kotlinImplements = msgDesc.options?.kotlinImplements
    )

    protected fun fieldsFromProto(ctx: Context, msgDesc: DescriptorProto, usedTypeNames: MutableSet<String>) =
        mutableSetOf<Int>().let { seenOneOfIndexes ->
            val usedFieldNames = mutableSetOf<String>()
            msgDesc.field.mapNotNull { field ->
                // Exclude any group fields
                if (field.type == FieldDescriptorProto.Type.TYPE_GROUP) null else field.oneofIndex.let { oneOfIndex ->
                    if (oneOfIndex == null) fromProto(ctx, field, msgDesc, usedFieldNames)
                    else if (seenOneOfIndexes.add(oneOfIndex)) msgDesc.oneofDecl[oneOfIndex].name?.let { name ->
                        val fieldTypeNames = mutableMapOf<String, String>()
                        File.Field.OneOf(
                            name = name,
                            fields = mutableSetOf<String>().let { usedFieldTypeNames ->
                                msgDesc.field.filter { it.oneofIndex == field.oneofIndex }.map {
                                    fieldTypeNames += it.name!! to namer.newTypeName(it.name!!, usedFieldTypeNames)
                                    fromProto(ctx, it, msgDesc, mutableSetOf(), true)
                                }
                            },
                            kotlinFieldTypeNames = fieldTypeNames,
                            kotlinFieldName = namer.newFieldName(name, usedFieldNames),
                            kotlinTypeName = namer.newTypeName(name, usedTypeNames)
                        )
                    } else null
                }
            }
        }

    protected fun fromProto(
        ctx: Context,
        fieldDesc: FieldDescriptorProto,
        msgDesc: DescriptorProto,
        usedFieldNames: MutableSet<String>,
        alwaysRequired: Boolean = false
    ) = fromProto(fieldDesc.type ?: error("Missing field type")).let { type ->
        File.Field.Standard(
            number = fieldDesc.number!!,
            name = fieldDesc.name!!,
            type = type,
            localTypeName = fieldDesc.typeName,
            repeated = fieldDesc.label == FieldDescriptorProto.Label.LABEL_REPEATED,
            optional = !alwaysRequired && fieldDesc.label == FieldDescriptorProto.Label.LABEL_OPTIONAL,
            packed = !type.neverPacked && (ctx.fileDesc.syntax == "proto3" || fieldDesc.options?.packed == true),
            map = supportMaps &&
                fieldDesc.label == FieldDescriptorProto.Label.LABEL_REPEATED &&
                fieldDesc.type == FieldDescriptorProto.Type.TYPE_MESSAGE &&
                ctx.findLocalMessage(fieldDesc.typeName!!)?.options?.mapEntry == true,
            kotlinFieldName = namer.newFieldName(fieldDesc.name!!, usedFieldNames),
            kotlinLocalTypeName =
                if (fieldDesc.typeName == null || fieldDesc.typeName!!.startsWith('.')) null
                else namer.newTypeName(fieldDesc.typeName!!, mutableSetOf()),
            kotlinNotnull = fieldDesc.options?.kotlinNotnull == true,
            kotlinDate = fieldDesc.options?.kotlinDate == true,
            kotlinBytesWrapper = fieldDesc.options?.kotlinBytesWrapper,
            implementsInterfaceProperty = overrides(ctx, fieldDesc.name!!, msgDesc.options?.kotlinImplements)
        )
    }

    private fun overrides(ctx: Context, name: String, superinterface: String?): Boolean {
        return superinterface != null && Platform.interfaceIncludesProperty(ctx, name, superinterface)
    }

    protected fun fromProto(type: FieldDescriptorProto.Type) = when (type) {
        FieldDescriptorProto.Type.TYPE_BOOL -> File.Field.Type.BOOL
        FieldDescriptorProto.Type.TYPE_BYTES -> File.Field.Type.BYTES
        FieldDescriptorProto.Type.TYPE_DOUBLE -> File.Field.Type.DOUBLE
        FieldDescriptorProto.Type.TYPE_ENUM -> File.Field.Type.ENUM
        FieldDescriptorProto.Type.TYPE_FIXED32 -> File.Field.Type.FIXED32
        FieldDescriptorProto.Type.TYPE_FIXED64 -> File.Field.Type.FIXED64
        FieldDescriptorProto.Type.TYPE_FLOAT -> File.Field.Type.FLOAT
        FieldDescriptorProto.Type.TYPE_GROUP -> TODO("Group types not supported")
        FieldDescriptorProto.Type.TYPE_INT32 -> File.Field.Type.INT32
        FieldDescriptorProto.Type.TYPE_INT64 -> File.Field.Type.INT64
        FieldDescriptorProto.Type.TYPE_MESSAGE -> File.Field.Type.MESSAGE
        FieldDescriptorProto.Type.TYPE_SFIXED32 -> File.Field.Type.SFIXED32
        FieldDescriptorProto.Type.TYPE_SFIXED64 -> File.Field.Type.SFIXED64
        FieldDescriptorProto.Type.TYPE_SINT32 -> File.Field.Type.SINT32
        FieldDescriptorProto.Type.TYPE_SINT64 -> File.Field.Type.SINT64
        FieldDescriptorProto.Type.TYPE_STRING -> File.Field.Type.STRING
        FieldDescriptorProto.Type.TYPE_UINT32 -> File.Field.Type.UINT32
        FieldDescriptorProto.Type.TYPE_UINT64 -> File.Field.Type.UINT64
        else -> error("Unknown type: $type")
    }

    data class Context(val fileDesc: FileDescriptorProto, val params: Map<String, String>) {
        fun findLocalMessage(name: String, parent: DescriptorProto? = null): DescriptorProto? {
            // Get the set to look in and the type name
            val (lookIn, typeName) =
                if (parent == null) fileDesc.messageType to name.removePrefix(".${fileDesc.`package`}.")
                else parent.nestedType to name
            // Go deeper if there's a dot
            typeName.indexOf('.').let {
                if (it == -1) return lookIn.find { it.name == typeName }
                return findLocalMessage(typeName.substring(it + 1), typeName.substring(0, it).let { parentTypeName ->
                    lookIn.find { it.name == parentTypeName }
                } ?: return null)
            }
        }
    }

    companion object : FileBuilder()
}