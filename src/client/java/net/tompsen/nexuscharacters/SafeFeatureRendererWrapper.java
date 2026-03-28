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
 * Must live outside the mixin package — Mixin forbids direct instantiation of classes
 * in packages owned by a mixin config (IllegalClassLoadError).
 */
@SuppressWarnings({"unchecked", "rawtypes"})
public final class SafeFeatureRendererWrapper<T extends LivingEntity, M extends EntityModel<T>>
        extends FeatureRenderer<T, M> {

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
        } catch (Exception ignored) {
            // Layer uses a server-only API or has missing world state — skip for preview.
        }
    }
}
