package app.joly0.patches.litho

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableClass
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod

private const val EXTENSION_CLASS = "Lapp/joly0/extension/PlaylistHeader;"

private const val CONTEXT_INTERFACE = "Lapp/joly0/extension/ConversionContextInterface;"
private const val CONTEXT_METHOD = "patch_joly0_getIdentifier"

private const val BUFFER_INTERFACE = "Lapp/joly0/extension/ProtoBufferInterface;"
private const val BUFFER_METHOD = "patch_joly0_encode"

/*
 * Adapted from morphe-patches (GPLv3), app.morphe.patches.shared.misc.litho.
 */

/**
 * Hands every Litho component's conversion context and protobuf buffer to extension code.
 *
 * This is a small part of what the official bundle's Litho filter does. The filter exists to hide
 * components, so most of it is matching machinery: a trie over identifiers and paths, filter
 * groups, and a return path that swaps the component for an empty one. None of that is wanted
 * here. All this needs is the pair (conversion context, protobuf buffer), which the create
 * component method already holds in p2 and p3.
 *
 * Two things make a naive port collide with the official bundle, and both are worked around here:
 *
 * - Upstream adds a method called `patch_getIdentifier` to the app's ConversionContext class. A
 *   second bundle adding the same method to the same class is a duplicate. The accessors added
 *   here carry a `patch_joly0_` prefix and their own interfaces, so both can be present.
 * - Upstream reads the buffer at the injection point, which needs free registers, and the helper
 *   that finds them lives in morphe-patches-library. That library cannot be a dependency of a
 *   template bundle: it drags build-time patcher classes into the extension's dex step and R8
 *   fails. Instead, the buffer is handed over untouched and decoded lazily on the extension side,
 *   so the injection is a single `invoke-static/range` over two parameters that are already
 *   consecutive, needing no free register at all.
 *
 * The accessors are added to the app's own classes rather than being called directly from the
 * extension because both classes are obfuscated and neither is guaranteed to be public. An
 * interface the extension owns is always reachable; the class implementing it need not be.
 */
internal val lithoComponentHookPatch = bytecodePatch(
    description = "Provides a hook for every Litho component's conversion context and buffer.",
) {
    execute {
        // region Expose the obfuscated identifier field of ConversionContext.

        val contextClass = ConversionContextToStringFingerprint.classDef
        val identifierField = ConversionContextToStringFingerprint.method.findIdentifierField()

        // On some versions ConversionContext inherits the field from an abstract class, which
        // cannot be read with iget-object from the subclass. No target version does, so rather
        // than carry the indirection upstream needs, fail with something readable if one appears.
        require(contextClass.superclass == "Ljava/lang/Object;") {
            "ConversionContext extends ${contextClass.superclass}, not Object. " +
                    "The identifier field is on the superclass and cannot be read directly."
        }

        contextClass.addAccessor(
            interfaceDescriptor = CONTEXT_INTERFACE,
            name = CONTEXT_METHOD,
            returnType = "Ljava/lang/String;",
            body = """
                iget-object v0, p0, $identifierField
                return-object v0
            """,
        )

        // endregion

        // region Expose the protobuf encode method of the Upb wrapper.

        val encodeMethod = ProtobufBufferEncodeFingerprint.method
        val bufferClass = ProtobufBufferEncodeFingerprint.classDef

        bufferClass.addAccessor(
            interfaceDescriptor = BUFFER_INTERFACE,
            name = BUFFER_METHOD,
            returnType = "[B",
            body = """
                invoke-virtual { p0 }, ${bufferClass.type}->${encodeMethod.name}()[B
                move-result-object v0
                return-object v0
            """,
        )

        // endregion

        // region Hand both over on every component.

        // Injected at the very start of the method, where p2 and p3 are still the parameters the
        // caller passed. They are consecutive, so invoke-static/range covers both and no register
        // has to be freed up. Injecting at the start also leaves the official bundle's own
        // injection sites, which are all relative to instructions further down, undisturbed.
        CreateComponentFingerprint.method.addInstruction(
            0,
            "invoke-static/range { p2 .. p3 }, " +
                    "$EXTENSION_CLASS->onComponent(Ljava/lang/Object;Ljava/lang/Object;)V",
        )

        // endregion
    }
}

/**
 * Adds [interfaceDescriptor] to this class along with the single method that implements it.
 *
 * The body gets two registers: v0 for scratch, and p0 for the instance.
 */
private fun MutableClass.addAccessor(
    interfaceDescriptor: String,
    name: String,
    returnType: String,
    body: String,
) {
    interfaces.add(interfaceDescriptor)
    methods.add(
        ImmutableMethod(
            type,
            name,
            listOf(),
            returnType,
            AccessFlags.PUBLIC.value or AccessFlags.FINAL.value,
            null,
            null,
            MutableMethodImplementation(2),
        ).toMutable().apply {
            addInstructions(0, body)
        }
    )
}

/**
 * Recovers the obfuscated identifier field from ConversionContext.toString().
 *
 * toString() appends the literal ", identifierProperty=" and then the field itself, so the
 * identifier is whatever the append straight after that literal is given. The read of the field
 * is not next to either of them: R8 hoists every field read in the method to the top, well above
 * the literal, so the register has to be walked backwards from the append to the instruction that
 * filled it.
 *
 * Upstream has a helper for this in morphe-patches-library; see the patch doc for why that
 * library is not a dependency here.
 *
 * @return the field in smali reference form, `Lclass;->name:Ltype;`.
 */
private fun MutableMethod.findIdentifierField(): String {
    val instructions = implementation!!.instructions.toList()

    val markerIndex = instructions.indexOfFirst { instruction ->
        (instruction.opcode == Opcode.CONST_STRING || instruction.opcode == Opcode.CONST_STRING_JUMBO) &&
                ((instruction as ReferenceInstruction).reference as StringReference).string == IDENTIFIER_PROPERTY
    }
    require(markerIndex >= 0) { "No '$IDENTIFIER_PROPERTY' literal in $definingClass->$name" }

    val markerRegister = (instructions[markerIndex] as OneRegisterInstruction).registerA

    // The literal is appended first and the identifier immediately after it, so the wanted append
    // is the first one that is passed something other than the literal's own register.
    val appendIndex = (markerIndex + 1 until instructions.size).firstOrNull { index ->
        val instruction = instructions[index]
        instruction.opcode == Opcode.INVOKE_VIRTUAL &&
                instruction.isStringBuilderAppend() &&
                (instruction as FiveRegisterInstruction).registerD != markerRegister
    }
    requireNotNull(appendIndex) { "No append of the identifier after '$IDENTIFIER_PROPERTY' in $definingClass->$name" }

    var register = (instructions[appendIndex] as FiveRegisterInstruction).registerD
    for (index in appendIndex - 1 downTo 0) {
        val instruction = instructions[index]

        if (instruction.opcode == Opcode.IGET_OBJECT) {
            if ((instruction as TwoRegisterInstruction).registerA != register) continue

            val field = (instruction as ReferenceInstruction).reference as FieldReference
            require(field.type == "Ljava/lang/String;") {
                "The identifier of $definingClass is a ${field.type}, not a String"
            }
            return "${field.definingClass}->${field.name}:${field.type}"
        }

        // R8 shuffles values between registers freely; follow the copy rather than give up.
        if (instruction.opcode == Opcode.MOVE_OBJECT ||
            instruction.opcode == Opcode.MOVE_OBJECT_FROM16 ||
            instruction.opcode == Opcode.MOVE_OBJECT_16
        ) {
            instruction as TwoRegisterInstruction
            if (instruction.registerA == register) register = instruction.registerB
            continue
        }

        // Anything else writing the register means it is not a plain field read.
        require(!(instruction.opcode == Opcode.MOVE_RESULT_OBJECT &&
                (instruction as OneRegisterInstruction).registerA == register)) {
            "The identifier of $definingClass comes from a method call, not a field"
        }
    }

    error("No field read feeding the identifier append in $definingClass->$name")
}

private fun Instruction.isStringBuilderAppend(): Boolean {
    val reference = (this as ReferenceInstruction).reference
    return reference is MethodReference &&
            reference.definingClass == "Ljava/lang/StringBuilder;" &&
            reference.name == "append"
}
