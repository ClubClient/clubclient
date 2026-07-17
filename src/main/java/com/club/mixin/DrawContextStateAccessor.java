package com.club.mixin;

//? if >=1.21.5 {
/*import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.render.state.GuiRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/^**
 * The one door into the GUI's recording buffer. Opened for {@link com.club.compat.ShapePipe}.
 *
 * <p><b>Why a mixin is unavoidable here, measured before it was written.</b> From 1.21.5 a DrawContext
 * does not draw, it RECORDS into a {@code GuiRenderState}, and drawing anything vanilla has no method for
 * means adding an element to that state. There is no public way to reach it, and that is not a guess:
 * {@code javap -p} on both jars (2026-07-17) says the field is {@code private final} on 1.21.8 and
 * package-private on 1.21.11, so neither version lets a mod read it — hence an {@code @Accessor}, and
 * hence one on BOTH. A sweep of every public member of {@code DrawContext}, {@code MinecraftClient} and
 * {@code GameRenderer} on 1.21.8 found no method returning a {@code GuiRenderState} at all; the only
 * public mentions are {@code GuiRenderer}'s constructor and {@code SpecialGuiElementRenderer.render},
 * neither of which hands one out. {@code DrawContext}'s own constructor TAKES one, which is the wrong
 * direction.
 *
 * <p><b>Why not {@code drawTexture} with a custom pipeline, the way {@link com.club.compat.IconPipe}
 * gets away without a mixin.</b> That route gives a shape exactly one UV pair and one tint colour, and a
 * rounded rect needs more: half-size, radius, thickness and a second colour for a gradient. Icons fit
 * through that keyhole because an icon IS a textured quad; a parametric SDF shape is not. The keyhole was
 * measured rather than assumed — see the vertex-carrier note in club:club_shape_vert.glsl.
 *
 * <p><b>This mixin must never load on 1.21.1.</b> That version's DrawContext has no {@code state} field
 * at all — it holds a {@code VertexConsumerProvider.Immediate} and draws immediately ({@code javap -p},
 * 1.21.1 jar, same day) — so mixin would refuse it at STARTUP with "No candidates were found matching
 * state" and take the shipped client down with it. Two independent guards keep that from happening: this
 * {@code //?} block deletes the class outright below 1.21.5, and {@code club.mixins.json} at the repo
 * root — the config 1.21.1 actually reads — does not name it. The per-version configs under
 * {@code versions/1.21.8} and {@code versions/1.21.11} are the only two that do.
 ^/
@Mixin(DrawContext.class)
public interface DrawContextStateAccessor {
    @Accessor("state") GuiRenderState club$state();
}*/
//?}
