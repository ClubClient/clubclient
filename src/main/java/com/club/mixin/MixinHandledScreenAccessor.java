package com.club.mixin;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * The ENTIRE mixin footprint of Item Scroll: accessors and invokers, zero injections.
 *
 * <p>{@code @Accessor}/{@code @Invoker} generate methods — they never rewrite a body and never claim an
 * injection point, so Sodium / REI / EMI / Inventory Profiles can all mixin {@link HandledScreen} and
 * nothing collides. Everything else the module needs (scroll, click, release, tick, render) comes from
 * Fabric's ScreenEvents/ScreenMouseEvents, which is the official seam for exactly this. This is the
 * deliberate opposite of {@code MixinHeldItemRenderer}, whose injection points are a minefield
 * (docs/ARCHITECTURE.md §8).</p>
 *
 * <p>Clicks are routed through the screen's own {@code onMouseClick} rather than
 * {@code interactionManager.clickSlot}: a modded screen that overrides it keeps its own semantics for
 * free, which is the difference between working inside an AE2 terminal and corrupting it.</p>
 */
@Mixin(HandledScreen.class)
public interface MixinHandledScreenAccessor {
    @Accessor("focusedSlot")      Slot club$focusedSlot();
    @Accessor("x")                int  club$x();
    @Accessor("y")                int  club$y();
    @Accessor("backgroundWidth")  int  club$backgroundWidth();
    @Accessor("backgroundHeight") int  club$backgroundHeight();

    /** {@code private Slot getSlotAt(double, double)} in 1.21.1 — the only way to hit-test during a drag. */
    @Invoker("getSlotAt")     Slot club$slotAt(double mouseX, double mouseY);
    @Invoker("onMouseClick")  void club$onMouseClick(Slot slot, int slotId, int button, SlotActionType type);
}
