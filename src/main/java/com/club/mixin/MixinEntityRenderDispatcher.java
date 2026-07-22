package com.club.mixin;

import com.club.combat.HitboxState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//? if <1.21.5 {
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import org.spongepowered.asm.mixin.Shadow;
//?} elif <1.21.11 {
/*import net.minecraft.client.render.VertexRendering;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.entity.state.EntityHitbox;
import net.minecraft.client.render.entity.state.EntityHitboxAndView;*/
//?}

/**
 * Recolours the F3+B entity hitbox and strips its "junk" lines — without ever forcing hitboxes ON and
 * without ever disabling depth-test. Every decision is read from {@link HitboxState}: {@code enabled} false
 * is a HARD no-op (the inject returns before touching anything, so vanilla draws its own unchanged box), and
 * only when it is true do we redraw the box in {@link HitboxState#resolveArgb()} and keep the vanilla view
 * vector / eye box iff {@link HitboxState#showJunk()}.
 *
 * <p><b>Reimplement-and-cancel, deliberately.</b> The vanilla method feeds its colour to two or three
 * separate {@code drawBox} calls, so there is no single arg or expression to modify — the clean seam is HEAD,
 * redraw, {@code ci.cancel()}. We reproduce vanilla's geometry exactly (same box, same eye-level ±0.01 red
 * box, same {@code getRotationVec × 2} blue view vector) and change only the box colour and whether the junk
 * is drawn. We do NOT reproduce the rare edge boxes (ender-dragon body parts, the yellow vehicle box); the
 * contract is "the main AABB box outline + the two junk lines", and those are outside it.
 *
 * <p><b>One target-class name, three different bodies — and it MOVES, not just changes.</b> The hitbox code
 * lives in a different place on each version, so the whole {@code @Mixin} + class is guarded per branch (like
 * {@code MixinBlockEntityRenderDispatcher}) rather than sharing a class declaration:
 *
 * <pre>{@code <1.21.5      EntityRenderDispatcher.renderHitbox(MatrixStack;VertexConsumer;Entity;FFFF)V   — colour is method args, drawBox via WorldRenderer
 * 1.21.5..1.21.10  EntityRenderDispatcher.renderHitboxes(MatrixStack;EntityHitboxAndView;VertexConsumer;F)V — colour is per-box EntityHitbox record fields, drawBox via VertexRendering
 * >=1.21.11        EntityRenderDispatcher itself is GONE (renamed EntityRenderManager) and the hitbox draw MOVED to EntityHitboxDebugRenderer}</pre>
 *
 * <p><b>The first boundary is 1.21.5, not 1.21.8 — corrected 2026-07-22 when the 1.21.6 node was added.</b>
 * It read {@code <1.21.8} from the day this file was written, which was harmless only because no node existed
 * between 1.21.1 and 1.21.8: the branch was true over [1.21.1, 1.21.8) but ever compiled at just the one
 * endpoint. Measured with javap against the named jars, {@code renderHitboxes} (render-state) is present from
 * <b>1.21.5</b>, and {@code WorldRenderer.drawBox} — which the args body calls — has zero hits in 1.21.6. Left
 * uncorrected, 1.21.6 would have taken the 1.21.1 body. Worse than a compile error: the args body's
 * {@code @Inject(method = "renderHitbox")} is a BARE-NAME selector and 1.21.6 does have a {@code renderHitbox}
 * (the 3-arg {@code (MatrixStack;VertexConsumer;EntityHitbox)}), so it would resolve, then fail the
 * callback-signature check at class load, and {@code required:true} would stop the client from starting.
 * javac cannot see that. Only the boundary can.
 *
 * <p><b>Why the render-state branch is {@code elif <1.21.11}, and why 1.21.11 gets NOTHING here.</b> This one
 * shared source file is COMPILED by all four version builds even though {@code club.mixins.json} only
 * registers it for 1.21.1 + 1.21.6 + 1.21.8. Measured against the 1.21.11 jar,
 * {@code net.minecraft.client.render.entity.EntityRenderDispatcher} does NOT exist (renamed
 * {@code EntityRenderManager}) and {@code EntityHitboxAndView} is gone. So both branches fall away on 1.21.11
 * and the file collapses to a bare {@code package} statement — a legal empty compilation unit that produces
 * no class, which is exactly right: 1.21.11 is not in this mixin's json list, and Task 3 adds
 * {@code MixinEntityHitboxDebugRenderer} for that version's GizmoDrawing path.
 */
//? if <1.21.5 {
@Mixin(EntityRenderDispatcher.class)
public class MixinEntityRenderDispatcher {

    @Shadow
    private static void drawVector(MatrixStack matrices, VertexConsumer vertices, Vector3f vector,
                                   Vec3d rotation, int color) {
        throw new AssertionError();   // @Shadow stub — the real body lives in the target class
    }

    @Inject(method = "renderHitbox", at = @At("HEAD"), cancellable = true)
    private static void club$recolorHitbox(MatrixStack matrices, VertexConsumer vertices, Entity entity,
                                           float tickDelta, float red, float green, float blue, CallbackInfo ci) {
        if (!HitboxState.enabled) return;   // untouched: vanilla draws its own hitbox

        int argb = HitboxState.resolveArgb();
        float a = ((argb >>> 24) & 0xFF) / 255.0f;
        float r = ((argb >>> 16) & 0xFF) / 255.0f;
        float g = ((argb >>> 8) & 0xFF) / 255.0f;
        float b = (argb & 0xFF) / 255.0f;

        Box box = entity.getBoundingBox().offset(-entity.getX(), -entity.getY(), -entity.getZ());
        // Native per-box line width is not reachable here (the LINES layer's LineWidth phase overwrites any
        // RenderSystem.lineWidth at flush, and this inject only has the VertexConsumer). So widen by stacking
        // concentric outlines: rings==1 at width 1.0 is exactly the single vanilla box (no visual change).
        int rings = HitboxState.rings();
        for (int i = 0; i < rings; i++) {
            double e = (i - (rings - 1) * 0.5) * HitboxState.RING_STEP;
            WorldRenderer.drawBox(matrices, vertices, e == 0.0 ? box : box.expand(e), r, g, b, a);
        }

        if (HitboxState.showJunk()) {
            if (entity instanceof LivingEntity) {
                WorldRenderer.drawBox(matrices, vertices,
                        box.minX, entity.getStandingEyeHeight() - 0.01, box.minZ,
                        box.maxX, entity.getStandingEyeHeight() + 0.01, box.maxZ,
                        1.0f, 0.0f, 0.0f, 1.0f);   // red eye-level line
            }
            drawVector(matrices, vertices, new Vector3f(0.0f, entity.getStandingEyeHeight(), 0.0f),
                    entity.getRotationVec(tickDelta).multiply(2.0), -16776961);   // blue view vector
        }
        ci.cancel();
    }
}
//?} elif <1.21.11 {
/*@Mixin(EntityRenderDispatcher.class)
public class MixinEntityRenderDispatcher {

    @Inject(method = "renderHitboxes(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/entity/state/EntityHitboxAndView;Lnet/minecraft/client/render/VertexConsumer;F)V", at = @At("HEAD"), cancellable = true)
    private static void club$recolorHitboxes(MatrixStack matrices, EntityHitboxAndView view, VertexConsumer vertices,
                                             float eyeHeight, CallbackInfo ci) {
        if (!HitboxState.enabled) return;   // untouched: vanilla draws its own hitboxes

        int argb = HitboxState.resolveArgb();
        float a = ((argb >>> 24) & 0xFF) / 255.0f;
        float r = ((argb >>> 16) & 0xFF) / 255.0f;
        float g = ((argb >>> 8) & 0xFF) / 255.0f;
        float b = (argb & 0xFF) / 255.0f;

        int rings = HitboxState.rings();   // 1 at width 1.0 => a single vanilla-identical box; wider stacks outlines
        for (EntityHitbox hitbox : view.hitboxes()) {
            matrices.push();
            matrices.translate(hitbox.offsetX(), hitbox.offsetY(), hitbox.offsetZ());
            for (int i = 0; i < rings; i++) {
                double e = (i - (rings - 1) * 0.5) * HitboxState.RING_STEP;   // native LineWidth is a per-pass global here, so widen via concentric outlines
                VertexRendering.drawBox(matrices, vertices,
                        hitbox.x0() - e, hitbox.y0() - e, hitbox.z0() - e,
                        hitbox.x1() + e, hitbox.y1() + e, hitbox.z1() + e,
                        r, g, b, a);   // colour overridden: our argb instead of hitbox.red()/green()/blue()
            }
            matrices.pop();
        }

        if (HitboxState.showJunk()) {
            VertexRendering.drawVector(matrices, vertices, new Vector3f(0.0f, eyeHeight, 0.0f),
                    new Vec3d(view.viewX(), view.viewY(), view.viewZ()).multiply(2.0), -16776961);   // blue view vector
        }
        ci.cancel();
    }
}*/
//?}
