package io.github.jmecn.fieldguideexport.cmd;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.github.jmecn.fieldguideexport.FieldGuideExportMod;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = FieldGuideExportMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class GuideExportClientCommands {

    private GuideExportClientCommands() {}

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("fieldguideexport");
        root.then(Commands.literal("run").executes(ctx -> GuideExport.run(ctx.getSource())));
        event.getDispatcher().register(root);
    }
}
