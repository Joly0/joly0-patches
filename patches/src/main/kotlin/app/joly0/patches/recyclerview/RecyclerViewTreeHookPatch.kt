package app.joly0.patches.recyclerview

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import java.lang.ref.WeakReference

/*
 * Adapted from morphe-patches (GPLv3), app.morphe.patches.youtube.misc.recyclerviewtree.
 * Vendored because morphe-patches is not published as a library.
 */

/**
 * Deliberately looser than the equivalent fingerprint upstream uses.
 *
 * Upstream matches a run of consecutive opcodes and then injects its own call into the middle of
 * that run. If the official bundle is applied first, the run no longer exists and an identical
 * fingerprint stops matching, so this bundle could only ever be used alone. Matching on the
 * constructor and its distinctive string survives another bundle having hooked the same method.
 */
private object RecyclerViewTreeObserverFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.CONSTRUCTOR),
    strings = listOf("LithoRVSLCBinder")
)

private lateinit var hookMethod: WeakReference<MutableMethod>
private var insertIndex = -1

/**
 * Hands every Litho-backed RecyclerView to extension code.
 *
 * The matched constructor is Litho's generic list binder, so this fires for every such list in
 * the app, not only for the flyout menus it was originally written for.
 */
internal val recyclerViewTreeHookPatch = bytecodePatch(
    description = "Provides a hook for every Litho-backed RecyclerView.",
) {
    execute {
        RecyclerViewTreeObserverFingerprint.method.let { method ->
            // The RecyclerView is put into a local by the first check-cast; inject straight
            // after it. Found by scanning rather than by a fixed match offset, so an injection
            // another bundle already made does not shift us onto the wrong instruction.
            val instructions = method.implementation!!.instructions
            val checkCastIndex = instructions.indexOfFirst { it.opcode == Opcode.CHECK_CAST }
            require(checkCastIndex >= 0) { "No check-cast in the Litho list binder constructor" }

            hookMethod = WeakReference(method)
            insertIndex = checkCastIndex + 1
        }
    }
}

/** The RecyclerView is the third parameter of the matched constructor. */
internal fun addRecyclerViewTreeHook(classDescriptor: String) {
    hookMethod.get()!!.addInstruction(
        insertIndex,
        "invoke-static/range { p2 .. p2 }, " +
                "$classDescriptor->onRecyclerViewCreated(Ljava/lang/Object;)V",
    )
}
