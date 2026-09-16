package app.joly0.patches.litho

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.methodCall
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode

/*
 * Adapted from morphe-patches (GPLv3), app.morphe.patches.shared.misc.litho.
 * Vendored rather than depended on: morphe-patches is not published as a library.
 */

/** Appears in ConversionContext.toString() immediately before the identifier field is read. */
internal const val IDENTIFIER_PROPERTY = ", identifierProperty="

/**
 * The ConversionContext class, located through its toString().
 *
 * The class name and all of its fields are obfuscated, but toString() spells the field names out
 * as literals, so the identifier field can be recovered from the instruction that follows the
 * [IDENTIFIER_PROPERTY] literal.
 */
internal object ConversionContextToStringFingerprint : Fingerprint(
    name = "toString",
    parameters = listOf(),
    returnType = "Ljava/lang/String;",
    strings = listOf(
        "ConversionContext{", // Partial string match.
        ", widthConstraint=",
        ", templateLoggerFactory=",
        ", rootDisposableContainer=",
        IDENTIFIER_PROPERTY
    )
)

/**
 * The wrapper that turns a Litho component into its protobuf bytes.
 *
 * YouTube 20.22 and newer always parse protobuf through this native Upb path, so the older
 * ByteBuffer hook is not needed here. UpbMessage itself is not obfuscated, which is what makes
 * the wrapper findable.
 */
internal object ProtobufBufferEncodeFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "[B",
    parameters = listOf(),
    filters = listOf(
        fieldAccess(
            opcode = Opcode.IGET_OBJECT,
            definingClass = "this",
            type = "Lcom/google/android/libraries/elements/adl/UpbMessage;"
        ),
        methodCall(
            definingClass = "Lcom/google/android/libraries/elements/adl/UpbMessage;",
            name = "jniEncode"
        )
    )
)

/**
 * The method that builds a component, with the conversion context in p2 and the protobuf wrapper
 * in p3.
 *
 * Matched on its two strings alone. The official bundle hooks this same method and injects into
 * the middle of it, so anything matching on opcode runs or offsets stops matching once that
 * bundle has been applied (see the note in RecyclerViewTreeHookPatch). String literals survive
 * any injection.
 */
internal object CreateComponentFingerprint : Fingerprint(
    returnType = "L",
    strings = listOf(
        "Element missing correct type extension",
        "Element missing type"
    )
)
