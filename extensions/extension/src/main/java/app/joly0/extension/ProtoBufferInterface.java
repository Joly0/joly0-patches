package app.joly0.extension;

/**
 * Added to the app's protobuf wrapper class during patching, together with the one method that
 * implements it. See {@link ConversionContextInterface} for why an interface is used.
 */
public interface ProtoBufferInterface {

    /**
     * The component tree encoded as protobuf bytes.
     * <p>
     * This encodes on every call, so only call it for a component whose buffer is actually wanted.
     */
    byte[] patch_joly0_encode();
}
