package com.club.compat;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;

/**
 * Worn equipment, across Minecraft versions.
 *
 * <p>Armour dissolved into the component system in stages, and the stages have <b>different boundaries</b>
 * — measured out of the Yarn mappings for 1.21.1 … 1.21.11 on 2026-07-16, because no amount of thinking
 * would have produced them:</p>
 *
 * <table>
 *   <tr><th>API</th><th>last version with it</th><th>what replaced it</th></tr>
 *   <tr><td>{@code ElytraItem}</td><td>1.21.1</td><td>the {@code GLIDER} component, from 1.21.2</td></tr>
 *   <tr><td>{@code ArmorItem}, {@code PlayerInventory.getArmorStack}</td><td>1.21.4</td>
 *       <td>{@code getEquippedStack(EquipmentSlot)} + the {@code EQUIPPABLE} component, from 1.21.5</td></tr>
 * </table>
 *
 * <p>Two boundaries, one version apart in the source and four apart in reality. Had this been written with a
 * single guessed {@code >=1.21.2} everywhere, the elytra branch would be right and the armour branch would be
 * wrong on 1.21.2 … 1.21.4 — and it would compile, because those versions still HAVE {@code ArmorItem}.</p>
 *
 * <p>Only {@link com.club.compat} knows any of this. {@code ArmorElement} calls the three methods below and
 * stays ordinary Java (docs/superpowers/specs/2026-07-16-multiversion-design.md §3).</p>
 */
public final class Equip {
    private Equip() {}

    /**
     * The armour worn in {@code slot}, using vanilla's ORIGINAL index order — 3 head, 2 chest, 1 legs,
     * 0 feet — which is the order {@code ArmorElement} already reads its pieces in.
     */
    public static ItemStack armor(PlayerEntity p, int slot) {
        //? if <1.21.5 {
        return p.getInventory().getArmorStack(slot);
        //?} else {
        /*return p.getEquippedStack(switch (slot) {
            case 3  -> net.minecraft.entity.EquipmentSlot.HEAD;
            case 2  -> net.minecraft.entity.EquipmentSlot.CHEST;
            case 1  -> net.minecraft.entity.EquipmentSlot.LEGS;
            default -> net.minecraft.entity.EquipmentSlot.FEET;
        });*/
        //?}
    }

    /** Does this stack let the player glide — i.e. is it an elytra? */
    public static boolean isGlider(ItemStack st) {
        //? if <1.21.2 {
        return st.getItem() instanceof net.minecraft.item.ElytraItem;
        //?} else {
        /*return st.contains(net.minecraft.component.DataComponentTypes.GLIDER);*/
        //?}
    }

    /**
     * The armour material's plain name — {@code "diamond"}, {@code "leather"}, {@code "netherite"} … — or
     * {@code ""} for anything that is not armour. {@code ArmorElement} switches its material tint on it.
     *
     * <p>Post-1.21.5 the name comes from the equippable component's asset id, whose path is the same word
     * the old {@code ArmorMaterial} key carried, so the tint table on the other side needs no changes.</p>
     */
    public static String materialName(ItemStack st) {
        //? if <1.21.5 {
        return st.getItem() instanceof net.minecraft.item.ArmorItem ai
                ? ai.getMaterial().getKey().map(k -> k.getValue().getPath()).orElse("")
                : "";
        //?} else {
        /*net.minecraft.component.type.EquippableComponent eq =
                st.get(net.minecraft.component.DataComponentTypes.EQUIPPABLE);
        return eq == null ? "" : eq.assetId().map(k -> k.getValue().getPath()).orElse("");*/
        //?}
    }
}
