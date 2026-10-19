package dev.expedition.arcane.grinding;

import io.redspace.ironsspellbooks.api.item.UpgradeData;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.IPresetSpellContainer;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.ISpellContainerMutable;
import io.redspace.ironsspellbooks.api.spells.SpellSlot;
import io.redspace.ironsspellbooks.item.UpgradeOrbItem;
import io.redspace.ironsspellbooks.registries.ItemRegistry;
import io.redspace.ironsspellbooks.item.UpgradeOrbTypeData;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

/** Pure copy-in/copy-out inspection. No getOrCreate, no initialization of the real input. */
public final class ArcaneSalvage {
    public record Result(ItemStack stack, long units, boolean changed) {}
    private static final ResourceLocation MANA_ORB = new ResourceLocation("irons_spellbooks", "mana_upgrade_orb");
    private static final ResourceLocation MANA_TYPE = new ResourceLocation("irons_spellbooks", "mana");
    private ArcaneSalvage() {}

    public static Result strip(ItemStack input) {
        if (input.isEmpty()) return new Result(ItemStack.EMPTY, 0, false);
        ItemStack output = input.copy();
        long units = 0;
        boolean changed = false;
        if (output.getItem() instanceof UpgradeOrbItem || output.is(ItemRegistry.UPGRADE_ORB.get())) {
            int value = MANA_ORB.equals(ForgeRegistries.ITEMS.getKey(output.getItem())) ? 24 : 8;
            if (UpgradeOrbTypeData.has(output) && MANA_TYPE.equals(UpgradeOrbTypeData.get(output).type().location())) value = 24;
            return new Result(ItemStack.EMPTY, (long) value * output.getCount(), true);
        }
        if (ISpellContainer.isSpellContainer(output)) {
            ISpellContainer container = ISpellContainer.get(output);
            if (container != null && !container.isEmpty()) {
                Set<ResourceLocation> innate = innateSpells(output);
                for (SpellSlot slot : container.getActiveSpells()) {
                    var spell = slot.getSpell();
                    if (slot.getLevel() <= 0 || spell == null || spell == SpellRegistry.none() || innate.contains(spell.getSpellResource())) continue;
                    if (SpellRegistry.REGISTRY.get().getKey(spell) == null) continue;
                    int rarity = spell.getRarity(slot.getLevel()).ordinal();
                    if (rarity >= 0 && rarity < 5) units = Math.addExact(units, (1L << rarity) * output.getCount());
                }
                // An explicit empty container prevents IPresetSpellContainer from restoring innate spells.
                // Preserve equipment semantics/capacity, but capacity and empty slots never earn any value.
                ISpellContainerMutable empty = ISpellContainer.create(container.getMaxSpellCount(), container.isSpellWheel(), container.mustEquip()).mutableCopy();
                empty.setImproved(container.isImproved());
                ISpellContainer.set(output, empty.toImmutable());
                output.getOrCreateTag().remove(ISpellContainer.LEGACY_NBT);
                changed = true;
            }
        }
        if (UpgradeData.hasUpgradeData(output)) {
            UpgradeData data = UpgradeData.getUpgradeData(output);
            for (var upgrade : data.upgrades().entrySet()) {
                int count = upgrade.getValue();
                if (count <= 0) continue;
                boolean mana = upgrade.getKey().unwrapKey().map(key -> MANA_TYPE.equals(key.location())).orElse(false);
                // containerItem is the native type's authoritative return item; no orb is returned here.
                mana |= upgrade.getKey().value().containerItem().map(stack -> MANA_ORB.equals(ForgeRegistries.ITEMS.getKey(stack.getItem()))).orElse(false);
                units = Math.addExact(units, Math.multiplyExact((long) count * (mana ? 24 : 8), output.getCount()));
            }
            UpgradeData.removeUpgradeData(output);
            output.getOrCreateTag().remove(UpgradeData.LEGACY_NBT);
            changed = true;
        }
        // A missing real container on an innate item is never a reason to mint essence.
        // It still needs a sealed empty result so its lazy preset initializer cannot restore spells.
        if (!ISpellContainer.isSpellContainer(output) && output.getItem() instanceof IPresetSpellContainer) {
            ISpellContainer defaults = presetDefaults(output);
            if (defaults != null && !defaults.isEmpty()) changed = true;
        }
        if (changed && output.getItem() instanceof IPresetSpellContainer && !ISpellContainer.isSpellContainer(output)) {
            // Orb removal must not turn a never-initialized preset into fresh innate spells later.
            ISpellContainer defaults = presetDefaults(output);
            ISpellContainer empty = defaults == null ? ISpellContainer.create(1, true, true)
                    : ISpellContainer.create(defaults.getMaxSpellCount(), defaults.isSpellWheel(), defaults.mustEquip());
            ISpellContainer.set(output, empty);
            output.getOrCreateTag().remove(ISpellContainer.LEGACY_NBT);
        }
        return new Result(output, units, changed);
    }

    private static Set<ResourceLocation> innateSpells(ItemStack input) {
        Set<ResourceLocation> innate = new HashSet<>();
        ISpellContainer defaults = presetDefaults(input);
        if (defaults != null) for (SpellSlot slot : defaults.getActiveSpells())
            if (slot.getSpell() != null) innate.add(slot.getSpell().getSpellResource());
        return innate;
    }
    private static ISpellContainer presetDefaults(ItemStack input) {
        if (!(input.getItem() instanceof IPresetSpellContainer preset)) return null;
        ItemStack probe = new ItemStack(input.getItem());
        preset.initializeSpellContainer(probe);
        return ISpellContainer.get(probe);
    }
}
