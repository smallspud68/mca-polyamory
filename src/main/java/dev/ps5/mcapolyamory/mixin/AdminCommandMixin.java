package dev.ps5.mcapolyamory.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.brigadier.context.CommandContext;
import dev.ps5.mcapolyamory.Poly;
import java.util.Set;
import java.util.UUID;
import net.conczin.mca.server.command.AdminCommand;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;

/** /mca-admin: a villager assumed dead, or a player's marriage reset, loses all partners. */
@Mixin(value = AdminCommand.class, remap = false)
public abstract class AdminCommandMixin {
    @WrapMethod(method = "assumeDead")
    private static void mcapoly$assumeDead(CommandContext<CommandSourceStack> ctx, UUID uuid, Operation<Void> original) {
        Poly.withContext(new Poly.EndContext(uuid, Set.of()), () -> original.call(ctx, uuid));
    }

    @WrapMethod(method = "resetMarriage")
    private static int mcapoly$resetMarriage(CommandContext<CommandSourceStack> ctx, Operation<Integer> original) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            return original.call(ctx);
        }
        return Poly.withContext(new Poly.EndContext(player.getUUID(), Set.of()), () -> original.call(ctx));
    }
}
