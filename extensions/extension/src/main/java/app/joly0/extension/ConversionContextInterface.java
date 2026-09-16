package app.joly0.extension;

/**
 * Added to the app's ConversionContext class during patching, together with the one method that
 * implements it.
 * <p>
 * The class itself is obfuscated and not necessarily public, so extension code can never name it.
 * An interface declared here is always reachable, whatever the class it ends up on is called.
 */
public interface ConversionContextInterface {

    /** The component's identifier, for example {@code page_header}. */
    String patch_joly0_getIdentifier();
}
