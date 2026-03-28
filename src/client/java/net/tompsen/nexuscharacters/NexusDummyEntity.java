package net.tompsen.nexuscharacters;

/**
 * Marker interface for dummy player entities used in the character preview UI.
 * Mixins use this to detect and skip mod-injected render layers that assume a
 * fully initialised server-side world (e.g. Supplementaries SlimedLayer,
 * Aether, Accessories) and would crash or produce garbage output when called
 * on these fake entities.
 */
public interface NexusDummyEntity {
}
