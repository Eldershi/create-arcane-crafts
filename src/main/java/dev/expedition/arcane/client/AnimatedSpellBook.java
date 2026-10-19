package dev.expedition.arcane.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

/** The native BookModel hinge layout, fitted with each spell book's original UVs. */
final class AnimatedSpellBook {
    boolean supports(ItemStack stack) {
        return !stack.isEmpty() && AnimatedSpellBookMeshes.books().containsKey(ForgeRegistries.ITEMS.getKey(stack.getItem()));
    }

    void render(ItemStack stack, float open, float page1, float page2, PoseStack pose,
                MultiBufferSource buffers, int light, int overlay) {
        var book = AnimatedSpellBookMeshes.books().get(ForgeRegistries.ITEMS.getKey(stack.getItem()));
        if (book == null) return;
        VertexConsumer buffer = buffers.getBuffer(RenderType.entityCutoutNoCull(new ResourceLocation(book.texture)));
        float angle = 1.25F * Mth.clamp(open, 0, 1);
        float reach = book.decor ? .38F : .30F;
        for (var part : book.parts) {
            String name = part.name;
            if (name.startsWith("flip_page") && open <= .025F) continue;
            pose.pushPose();
            switch (name) {
                case "left_lid" -> { pose.translate(0, 0, -book.thickness / 16); pose.mulPose(Axis.YP.rotation(Mth.PI + angle)); }
                case "right_lid" -> { pose.translate(0, 0, book.thickness / 16); pose.mulPose(Axis.YP.rotation(-angle)); }
                case "seam" -> {
                    pose.translate(-(reach * Mth.sin(angle) + .035F) / 16, 0, 0);
                    pose.mulPose(Axis.YP.rotation(Mth.HALF_PI));
                    pose.scale((book.thickness + reach * Mth.cos(angle)) / (book.thickness + reach), 1, 1);
                }
                default -> {
                    pose.translate(book.thickness * Mth.sin(angle) / 16, 0,
                            name.equals("flip_page1") ? -.012F * book.thickness / 16
                                    : name.equals("flip_page2") ? .012F * book.thickness / 16 : 0);
                    float yaw = switch (name) {
                        case "left_pages" -> angle;
                        case "right_pages" -> -angle;
                        case "flip_page1" -> angle * (1 - 2 * page1);
                        default -> angle * (1 - 2 * page2);
                    };
                    pose.mulPose(Axis.YP.rotation(yaw));
                }
            }
            for (var mesh : part.meshes) {
                // Native entity render types consume quads; duplicate each triangle's last vertex.
                for (int base = 0; base < mesh.p.length / 3; base += 3) {
                    for (int corner : new int[]{0, 1, 2, 2}) {
                        int i = base + corner;
                        buffer.vertex(pose.last().pose(), mesh.p[i * 3], mesh.p[i * 3 + 1], mesh.p[i * 3 + 2])
                                .color(255, 255, 255, 255).uv(mesh.uv[i * 2], mesh.uv[i * 2 + 1])
                                .overlayCoords(overlay).uv2(light)
                                .normal(pose.last().normal(), mesh.n[i * 3], mesh.n[i * 3 + 1], mesh.n[i * 3 + 2]).endVertex();
                    }
                }
            }
            pose.popPose();
        }
    }
}
