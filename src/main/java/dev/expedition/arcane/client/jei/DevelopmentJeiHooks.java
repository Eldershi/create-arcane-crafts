package dev.expedition.arcane.client.jei;

import dev.expedition.arcane.ArcaneIndustry;
import mezz.jei.api.runtime.IJeiRuntime;

/** Optional evidence writers are available only in the separately built prototype JAR. */
final class DevelopmentJeiHooks {
    private DevelopmentJeiHooks() {}

    static void write(IJeiRuntime runtime, ArcaneRecipeDisplays.Snapshot expected,
                      int hiddenPlaceholders, int hiddenGenericAssemblies) {
        if (!Boolean.getBoolean("arcane.prototype")) return;
        try {
            Class.forName("dev.expedition.arcane.client.jei.JeiPrototypeVerification")
                    .getMethod("write", IJeiRuntime.class, ArcaneRecipeDisplays.Snapshot.class,
                            int.class, int.class)
                    .invoke(null, runtime, expected, hiddenPlaceholders, hiddenGenericAssemblies);
        } catch (ClassNotFoundException absent) {
            ArcaneIndustry.LOGGER.info("Prototype JEI checks are not included in this production build");
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Could not write prototype JEI checks", failure);
        }
    }
}
