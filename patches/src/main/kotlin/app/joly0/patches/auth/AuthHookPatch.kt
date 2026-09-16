package app.joly0.patches.auth

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction

private const val EXTENSION_CLASS = "Lapp/joly0/extension/AuthUtils;"

/**
 * SPIKE. Can a standalone bundle capture the signed-in user's InnerTube auth headers on its own,
 * without any of morphe-patches' machinery?
 *
 * That is the last unknown blocking a standalone playlist bundle: the playlist id already comes
 * from the component buffer, and the request plumbing is ordinary code, but the auth header is
 * only obtainable by hooking the app where it builds requests.
 */
internal val authHookPatch = bytecodePatch(
    description = "Captures request headers to prove a standalone bundle can authenticate.",
) {
    execute {
        getBuildRequestFingerprint().let { fingerprint ->
            // Read the registers straight off the instructions rather than using
            // app.morphe.util.registersUsed: pulling morphe-patches-library in as a dependency
            // drags build-time patcher classes into the extension's dex step and fails R8.
            //
            // newUrlRequestBuilder(String, Callback, Executor): C is the receiver, D the url.
            val match = fingerprint.instructionMatches.first()
            val urlRegister = (match.instruction as FiveRegisterInstruction).registerD
            // Map.entrySet(): C is the map itself.
            val mapRegister =
                (fingerprint.instructionMatches[1].instruction as FiveRegisterInstruction).registerC

            fingerprint.method.addInstructions(
                match.index,
                "invoke-static { v$urlRegister, v$mapRegister }, " +
                        "$EXTENSION_CLASS->setRequestHeaders(Ljava/lang/String;Ljava/util/Map;)V"
            )
        }
    }
}
