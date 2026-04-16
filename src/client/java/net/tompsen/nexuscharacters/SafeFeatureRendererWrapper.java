package net.tompsen.nexuscharacters;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;

/**
 * Wraps any FeatureRenderer and silently swallows exceptions thrown during render.
 *
 * Used by FeatureRendererMixin to allow mod armor/weapon layers (Archer's Expansion,
 * Armory RPG Series, Marium's Soulslike Weaponry, etc.) to render on preview dummy
 * entities, while layers that crash due to server-only APIs (PlayerLookup.tracking,
 * attachment syncing from Supplementaries, Aether, Accessories) are skipped silently.
 *
 * When a layer crashes, the entity's UUID is recorded in {@link #crashedEntityUuids}
 * so that the UI can automatically disable equipment rendering for that character.
 *
 * Must live outside the mixin package — Mixin forbids direct instantiation of classes
 * in packages owned by a mixin config (IllegalClassLoadError).
 */
@SuppressWarnings({"unchecked", "rawtypes"})
public final class SafeFeatureRendererWrapper<T extends LivingEntity, M extends EntityModel<T>>
        extends FeatureRenderer<T, M> {

    /**
     * UUIDs of dummy entities whose feature-renderer layer crashed at least once.
     * Populated during render; consumed by CharacterSelectionScreen to auto-disable
     * equipment for the affected character.
     */
    public static final java.util.Set<java.util.UUID> crashedEntityUuids =
            java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());

    private final FeatureRenderer delegate;

    public SafeFeatureRendererWrapper(FeatureRenderer<?, ?> delegate) {
        super(null);
        this.delegate = delegate;
    }

    @Override
    public void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                       int light, T entity,
                       float limbAngle, float limbDistance,
                       float tickDelta, float animationProgress,
                       float headYaw, float headPitch) {
        try {
            delegate.render(matrices, vertexConsumers, light, entity,
                    limbAngle, limbDistance, tickDelta, animationProgress,
                    headYaw, headPitch);
        } catch (Throwable ignored) {
            // Layer uses a server-only API or has missing world state — skip for preview.
            // Catch Throwable (not just Exception) because mod layers outside a real world
            // commonly throw Error subclasses (NullPointerError, etc.) rather than checked exceptions.
            // Record the entity so the UI can auto-disable equipment for this character.
            if (entity.getUuid() != null) {
                crashedEntityUuids.add(entity.getUuid());
            }
        }
    }
}
