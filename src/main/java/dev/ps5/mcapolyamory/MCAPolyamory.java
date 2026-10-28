package dev.ps5.mcapolyamory;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import net.conczin.mca.entity.ai.relationship.RelationshipState;
import net.conczin.mca.item.BabyItem;
import net.conczin.mca.server.world.data.FamilyTreeNode;
import net.conczin.mca.server.world.data.PlayerSaveData;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MCAPolyamory implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("mca_polyamory");

    @Override
    public void onInitialize() {
        PolyConfig.load();
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("polyamory")
            .executes(MCAPolyamory::list)
            .then(Commands.literal("list").executes(MCAPolyamory::list))
            .then(Commands.literal("primary")
                .then(Commands.argument("partner", StringArgumentType.greedyString())
                    .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(partnerNames(ctx), builder))
                    .executes(MCAPolyamory::primary)))
            .then(Commands.literal("procreate")
                .then(Commands.argument("player", StringArgumentType.greedyString())
                    .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                        partners(ctx.getSource().getPlayerOrException()).filter(FamilyTreeNode::isPlayer).map(FamilyTreeNode::getName), builder))
                    .executes(MCAPolyamory::procreate)))
            .then(Commands.literal("separate")
                .then(Commands.argument("player", StringArgumentType.greedyString())
                    .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                        partners(ctx.getSource().getPlayerOrException()).filter(FamilyTreeNode::isPlayer).map(FamilyTreeNode::getName), builder))
                    .executes(MCAPolyamory::separate))));
    }

    private static Stream<FamilyTreeNode> partners(ServerPlayer player) {
        FamilyTreeNode me = PlayerSaveData.get(player).getFamilyEntry();
        return me.lookup(Poly.partners(me));
    }

    private static Stream<String> partnerNames(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return partners(ctx.getSource().getPlayerOrException()).map(FamilyTreeNode::getName);
    }

    private static Optional<FamilyTreeNode> findPartner(ServerPlayer player, String name) {
        return partners(player).filter(n -> n.getName().equalsIgnoreCase(name)).findFirst();
    }

    private static int list(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        FamilyTreeNode me = PlayerSaveData.get(player).getFamilyEntry();
        if (Poly.partners(me).findAny().isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("You have no partners.").withStyle(ChatFormatting.GRAY), false);
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal("Your partners:").withStyle(ChatFormatting.LIGHT_PURPLE), false);
        Poly.partners(me).forEach(uuid -> {
            String name = me.getRoot().getOrEmpty(uuid).map(FamilyTreeNode::getName).orElse(uuid.toString());
            String state = Poly.stateWith(me, uuid).map(MCAPolyamory::describe).orElse("?");
            boolean primary = uuid.equals(me.partner());
            ctx.getSource().sendSuccess(() -> Component.literal(" - " + name + " (" + state + ")" + (primary ? " [primary]" : ""))
                .withStyle(primary ? ChatFormatting.GOLD : ChatFormatting.WHITE), false);
        });
        return 1;
    }

    private static String describe(RelationshipState state) {
        return state.isMarried() ? "married" : state.name().toLowerCase(Locale.ROOT);
    }

    /** MCA's /mca procreate, /mca separate and adoption use the primary partner; this picks who that is. */
    private static int primary(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String name = StringArgumentType.getString(ctx, "partner");
        Optional<FamilyTreeNode> partner = findPartner(player, name);
        if (partner.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal(name + " is not your partner."));
            return 0;
        }
        FamilyTreeNode me = PlayerSaveData.get(player).getFamilyEntry();
        Poly.makePrimary(me, partner.get().id());
        ctx.getSource().sendSuccess(() -> Component.literal(partner.get().getName() + " is now your primary partner.").withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    /** Pending requests: requester -> (spouse, expiry). Both spouses must ask within 10 seconds, like /mca procreate. */
    private static final Map<UUID, Map.Entry<UUID, Long>> PROCREATE_REQUESTS = new HashMap<>();

    /** /mca procreate only works with the primary spouse; this works with any player spouse. */
    private static int procreate(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String name = StringArgumentType.getString(ctx, "player");
        Optional<FamilyTreeNode> partner = findPartner(player, name).filter(FamilyTreeNode::isPlayer);
        if (partner.isEmpty() || !PlayerSaveData.get(player).isMarriedTo(partner.get().id())) {
            ctx.getSource().sendFailure(Component.literal("You are not married to a player called " + name + "."));
            return 0;
        }
        ServerPlayer spouse = player.level().getServer().getPlayerList().getPlayer(partner.get().id());
        if (spouse == null) {
            ctx.getSource().sendFailure(Component.translatable("server.spouseNotPresent"));
            return 0;
        }

        long now = System.currentTimeMillis();
        PROCREATE_REQUESTS.values().removeIf(request -> request.getValue() < now);
        Map.Entry<UUID, Long> theirs = PROCREATE_REQUESTS.get(spouse.getUUID());
        if (theirs != null && theirs.getKey().equals(player.getUUID())) {
            PROCREATE_REQUESTS.remove(spouse.getUUID());
            spouse.addItem(BabyItem.createItem(spouse, player, spouse.getRandom().nextLong()));
            player.sendSystemMessage(Component.translatable("server.procreationSuccessful").withStyle(ChatFormatting.GREEN));
            spouse.sendSystemMessage(Component.translatable("server.procreationSuccessful").withStyle(ChatFormatting.GREEN));
            return 1;
        }

        PROCREATE_REQUESTS.put(player.getUUID(), Map.entry(spouse.getUUID(), now + 10000L));
        ctx.getSource().sendSuccess(() -> Component.literal("Asked " + spouse.getScoreboardName() + " for a baby.").withStyle(ChatFormatting.LIGHT_PURPLE), false);
        spouse.sendSystemMessage(Component.literal(player.getScoreboardName() + " wants a baby with you. To accept, type /polyamory procreate "
            + player.getScoreboardName() + " within 10 seconds.").withStyle(ChatFormatting.LIGHT_PURPLE));
        return 1;
    }

    /** Ends a player marriage with a specific player (villagers are divorced through their menu). */
    private static int separate(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String name = StringArgumentType.getString(ctx, "player");
        Optional<FamilyTreeNode> partner = findPartner(player, name).filter(FamilyTreeNode::isPlayer);
        if (partner.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal(name + " is not a player you are with."));
            return 0;
        }
        UUID other = partner.get().id();
        Poly.withContext(new Poly.EndContext(null, Set.of(player.getUUID(), other)), () -> {
            PlayerSaveData.get(player).endRelationShip(RelationshipState.SINGLE);
            PlayerSaveData.get(player.level(), other).endRelationShip(RelationshipState.SINGLE);
            return null;
        });
        ctx.getSource().sendSuccess(() -> Component.literal("You have separated from " + partner.get().getName() + ".").withStyle(ChatFormatting.YELLOW), false);
        ServerPlayer otherPlayer = player.level().getServer().getPlayerList().getPlayer(other);
        if (otherPlayer != null) {
            otherPlayer.sendSystemMessage(Component.literal(player.getScoreboardName() + " has separated from you.").withStyle(ChatFormatting.RED));
        }
        return 1;
    }
}
