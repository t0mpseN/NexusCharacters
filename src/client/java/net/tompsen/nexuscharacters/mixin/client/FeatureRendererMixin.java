package net.tompsen.nexuscharacters.mixin.client;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.tompsen.nexuscharacters.NexusDummyEntity;
import net.tompsen.nexuscharacters.SafeFeatureRendererWrapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

@Mixin(LivingEntityRenderer.class)
public abstract class FeatureRendererMixin {

    /**
     * For dummy preview entities, wrap each feature renderer with SafeFeatureRendererWrapper
     * so that layers which crash (server-only API calls from Supplementaries, Aether,
     * Accessories, etc.) are silently skipped, while layers that work correctly
     * (armor, weapons from any mod) render normally.
     *
     * FeatureRenderer.render is abstract — @Inject on it causes a NPE in Mixin.
     * Redirecting List.iterator() inside LivingEntityRenderer.render is the correct hook.
     *
     * SafeFeatureRendererWrapper lives in the main mod package, not here — Mixin throws
     * IllegalClassLoadError if a class inside the mixin package is instantiated directly.
     */
    @Redirect(
            method = "render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/List;iterator()Ljava/util/Iterator;"
            )
    )
    private Iterator<FeatureRenderer<?, ?>> wrapFeaturesForDummy(List<FeatureRenderer<?, ?>> features,
                                                                   LivingEntity entity,
                                                                   float yaw, float tickDelta,
                                                                   MatrixStack matrices,
                                                                   VertexConsumerProvider vertexConsumers,
                                                                   int light) {
        if (!(entity instanceof NexusDummyEntity)) {
            return features.iterator();
        }

        final Iterator<FeatureRenderer<?, ?>> delegate = features.iterator();
        return new Iterator<FeatureRenderer<?, ?>>() {
            @Override
            public boolean hasNext() {
                return delegate.hasNext();
            }

            @Override
            public FeatureRenderer<?, ?> next() {
                if (!delegate.hasNext()) throw new NoSuchElementException();
                return new SafeFeatureRendererWrapper<>(delegate.next());
            }
        };
    }
}
