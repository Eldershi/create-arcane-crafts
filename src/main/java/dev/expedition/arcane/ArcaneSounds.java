package dev.expedition.arcane;

import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Fixed samples approved in the chamber preview; resources remain in their native packs. */
public final class ArcaneSounds {
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(
            ForgeRegistries.SOUND_EVENTS, ArcaneIndustry.MOD_ID);
    public static final RegistryObject<SoundEvent> BOOK_OPEN = sound("book_open");
    public static final RegistryObject<SoundEvent> BOOK_CLOSE = sound("book_close");
    public static final RegistryObject<SoundEvent> SELECT = sound("select");
    public static final RegistryObject<SoundEvent> MATERIAL = sound("material");
    public static final RegistryObject<SoundEvent> WORK = sound("work");
    public static final RegistryObject<SoundEvent> SUCCESS = sound("success");

    private ArcaneSounds() {}
    private static RegistryObject<SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(ArcaneIndustry.id(name)));
    }
    public static void register(IEventBus bus) { SOUNDS.register(bus); }
}
