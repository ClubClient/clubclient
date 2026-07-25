package com.club.mixin;

import com.club.config.ClubConfig;
import com.club.modules.saturation.SaturationRender;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Two unrelated jobs on the vanilla HUD, kept in one mixin because both are cheap TAIL/HEAD hooks on
 * {@code InGameHud} and neither needs the other:
 *
 * <ol>
 *   <li><b>Hide the vanilla status-effect overlay</b> while Club's Potion HUD is drawing (gated on
 *       {@code hud.potions}).</li>
 *   <li><b>Saturation module</b> (AppleSkin-style): after vanilla has drawn the food and health bars, overlay
 *       the hidden saturation reserve, a held-food restore preview, and — single-player only — the exhaustion
 *       line. Gated on {@code saturation}; off means the bars are never touched.</li>
 * </ol>
 *
 * <p>Both {@code renderFood} and {@code renderHealthBar} were measured to have one identical descriptor across
 * all four versions (1.21.1/1.21.6/1.21.8/1.21.11), so these injects carry no {@code //?} split. The overlay
 * draws through {@link DrawContext#fill} exactly as vanilla drew two instructions earlier, so the 1.21.5+
 * render-recording change does not reach it.</p>
 */
@Mixin(InGameHud.class)
public class MixinInGameHud {

    @Inject(method = "renderStatusEffectOverlay", at = @At("HEAD"), cancellable = true)
    private void club$hideVanillaEffects(CallbackInfo ci) {
        if (ClubConfig.get().hud.potions) {
            ci.cancel();
        }
    }

    /**
     * The two ints are the measured anchors: {@code top} = the food row's top y, {@code right} = its right
     * edge. TAIL so vanilla's own shanks are already on screen underneath.
     */
    @Inject(method = "renderFood", at = @At("TAIL"))
    private void club$saturationFood(DrawContext ctx, PlayerEntity player, int top, int right, CallbackInfo ci) {
        if (ClubConfig.get().saturation) {
            SaturationRender.onRenderFood(ctx, player, top, right);
        }
    }

    /**
     * Ghost hearts for the health natural regen would restore. The handler mirrors the full target parameter
     * list (measured identical on all four) up to the {@link CallbackInfo}; only {@code x}, {@code y},
     * {@code maxHealth} and {@code health} are used.
     */
    @Inject(method = "renderHealthBar", at = @At("TAIL"))
    private void club$saturationHealth(DrawContext ctx, PlayerEntity player, int x, int y, int lines,
                                       int regeneratingHeartIndex, float maxHealth, int lastHealth, int health,
                                       int absorption, boolean blinking, CallbackInfo ci) {
        if (ClubConfig.get().saturation) {
            SaturationRender.onRenderHealth(ctx, player, x, y, maxHealth, health);
        }
    }
}
