package com.club.mixin;

import com.club.combat.HitboxState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//? if >=1.21.11 {
/*import net.minecraft.client.render.DrawStyle;
import net.minecraft.client.render.debug.EntityHitboxDebugRenderer;
import net.minecraft.world.debug.gizmo.GizmoDrawing;*/
//?}

/**
 * The 1.21.11 twin of {@link MixinEntityRenderDispatcher}. On this version the F3+B hitbox draw was pulled out
 * of the (renamed) render manager and moved into {@code net.minecraft.client.render.debug.EntityHitboxDebugRenderer},
 * which no longer feeds a {@code VertexConsumer} — it calls the NEW immediate
 * {@code net.minecraft.world.debug.gizmo.GizmoDrawing} API ({@code box}/{@code point}/{@code arrow}, each
 * returning a {@code VisibilityConfigurable}). Because that is a fluent, per-call immediate API there is no
 * single arg or expression to {@code @Redirect}, so the clean seam is the same one Task 2 used: inject at HEAD,
 * redraw, {@code ci.cancel()}.
 *
 * <p><b>The exact same state contract as Task 2.</b> Everything is read from {@link HitboxState}: when
 * {@code enabled} is false the inject returns immediately and vanilla draws its own hitbox unchanged (a hard
 * no-op); when it is true we redraw the main AABB box + centre point in {@link HitboxState#resolveArgb()} and
 * keep the vanilla "junk" (the red eye-level box on {@link LivingEntity} and the blue view-direction arrow)
 * ONLY while {@link HitboxState#showJunk()} is true. We never force hitboxes on and never touch depth-test /
 * see-through.
 *
 * <p><b>Geometry matched to vanilla bytecode.</b> Unlike 1.21.1 (where the box is offset to the origin and the
 * matrix stack is translated to the entity), the gizmo API draws in WORLD space, so the interpolation shift is
 * applied to the box itself: {@code delta = getLerpedPos(tickDelta) - getEntityPos()}, and the box is
 * {@code getBoundingBox().offset(delta)}. The eye-level box therefore sits at {@code box.minY +
 * getStandingEyeHeight() ± 0.01} (world Y, not a local 0), and the point/arrow use the lerped world position —
 * exactly as vanilla computes them. We reproduce only the main box, the point and the two junk lines; the rare
 * edge draws (vehicle box, ender-dragon parts, the server velocity arrow) are outside this contract and left
 * to vanilla by the cancel-vs-return branch.
 *
 * <p><b>Guarded to 1.21.11 only.</b> {@code EntityHitboxDebugRenderer}, {@code GizmoDrawing} and
 * {@code DrawStyle} exist only from 1.21.11, and this one source file is compiled by all four version builds,
 * so the whole {@code @Mixin} + class + its version-specific imports live inside a {@code //? if >=1.21.11}
 * block (mirroring {@code MixinBlockEntityRenderDispatcher} / {@code DrawContextStateAccessor}). On
 * 1.21.1 / 1.21.8 the block falls away and the file collapses to a bare {@code package} statement — a legal
 * empty compilation unit that emits no class, which is correct: those versions are served by
 * {@code MixinEntityRenderDispatcher} and this mixin is listed only in the 1.21.11 {@code club.mixins.json}.
 */
//? if >=1.21.11 {
/*@Mixin(EntityHitboxDebugRenderer.class)
public class MixinEntityHitboxDebugRenderer {

    @Inject(method = "drawHitbox", at = @At("HEAD"), cancellable = true)
    private void club$recolorHitbox(Entity entity, float tickDelta, boolean serverSide, CallbackInfo ci) {
        if (!HitboxState.enabled) return;   // untouched: vanilla draws its own hitbox

        int color = HitboxState.resolveArgb();
        Vec3d lerpedPos = entity.getLerpedPos(tickDelta);
        Vec3d delta = lerpedPos.subtract(entity.getEntityPos());

        // Native line width: DrawStyle carries a strokeWidth (vanilla default 2.5), which BoxGizmo feeds to
        // GizmoDrawer.addLine -> VertexConsumer.lineWidth(...) i.e. MC's SHADER line expansion, not raw
        // glLineWidth, so it thickens reliably (measured against the 1.21.11 jar). width 1.0 => stroked(color)
        // (==2.5) is byte-identical to today; above that scales the stroke.
        float w = HitboxState.lineWidth;
        DrawStyle boxStyle = w <= 1.0f ? DrawStyle.stroked(color) : DrawStyle.stroked(color, 2.5f * w);
        GizmoDrawing.box(entity.getBoundingBox().offset(delta), boxStyle);
        GizmoDrawing.point(lerpedPos, color, 2.0f);

        if (HitboxState.showJunk()) {
            if (entity instanceof LivingEntity) {
                Box box = entity.getBoundingBox().offset(delta);
                float eyeHeight = entity.getStandingEyeHeight();
                GizmoDrawing.box(new Box(
                        box.minX, box.minY + eyeHeight - 0.01, box.minZ,
                        box.maxX, box.minY + eyeHeight + 0.01, box.maxZ),
                        DrawStyle.stroked(-65536));   // red eye-level box
            }
            Vec3d eyePos = lerpedPos.add(0.0, entity.getStandingEyeHeight(), 0.0);
            GizmoDrawing.arrow(eyePos, eyePos.add(entity.getRotationVec(tickDelta).multiply(2.0)),
                    -16776961);   // blue view vector
        }
        ci.cancel();
    }
}*/
//?}
