package com.chainmining;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;
import net.fabricmc.loader.api.metadata.ModMetadata;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

public class ChainMiningMod implements ModInitializer {
    public static final String MOD_ID = "chain-mining";
    private static final Map<Player, Boolean> ENABLED = new WeakHashMap<>();

    public static boolean isEnabled(Player player) {
        return ENABLED.getOrDefault(player, false);
    }

    public static void setEnabled(Player player, boolean enabled) {
        ENABLED.put(player, enabled);
    }

    @Override
    public void onInitialize() {
        checkDependencies();

        PayloadTypeRegistry.serverboundPlay().register(ChainMiningPacket.TYPE, ChainMiningPacket.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(ChainMiningPacket.TYPE, (payload, context) -> {
            setEnabled(context.player(), payload.enabled());
        });

        // Chain mining: use PlayerBlockBreakEvents.AFTER to BFS same-type blocks
        PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
            if (!(player instanceof ServerPlayer serverPlayer)) return;
            if (!isEnabled(player)) return;
            if (level.isClientSide()) return;

            ServerLevel serverLevel = (ServerLevel) level;
            ItemStack tool = serverPlayer.getMainHandItem();

            // Only tools with durability can chain (no blocks/fists/sticks)
            if (tool.isEmpty()) return;
            int maxDurability = tool.getMaxDamage();
            if (maxDurability <= 0) return;

            int currentDamage = tool.getDamageValue();
            int remainingUses = maxDurability - currentDamage;
            if (remainingUses <= 0) return;

            // BFS find up to min(64, remainingUses) adjacent same-type blocks
            List<BlockPos> chain = bfs(serverLevel, pos, state, Math.min(64, remainingUses), tool, serverPlayer);

            if (chain.isEmpty()) return;

            int toBreak = Math.min(chain.size(), remainingUses);

            for (int i = 0; i < toBreak; i++) {
                BlockPos target = chain.get(i);
                BlockState targetState = serverLevel.getBlockState(target);

                if (!targetState.is(state.getBlock())) continue;

                serverLevel.removeBlock(target, false);
                Block.dropResources(targetState, serverLevel, target, null, player, tool.copy());

                if (!tool.isEmpty()) {
                    tool.hurtAndBreak(1, serverLevel, serverPlayer, (Item it) -> {});
                }
            }
        });
    }

    private static List<BlockPos> bfs(ServerLevel level, BlockPos start, BlockState target, int maxBlocks, ItemStack tool, ServerPlayer player) {
        Set<BlockPos> visited = new HashSet<>();
        Queue<BlockPos> queue = new ArrayDeque<>();
        List<BlockPos> result = new ArrayList<>();

        queue.add(start);
        visited.add(start);

        while (!queue.isEmpty() && result.size() < maxBlocks) {
            BlockPos cur = queue.poll();
            BlockState curState = level.getBlockState(cur);

            // Only chain if same block type AND tool correctly matches the block
            if (curState.is(target.getBlock()) && !cur.equals(start)
                && canToolMine(tool, curState)) {
                result.add(cur);
            }

            for (BlockPos neighbor : new BlockPos[]{
                cur.north(), cur.south(), cur.east(), cur.west(), cur.above(), cur.below()
            }) {
                if (!visited.contains(neighbor)) {
                    visited.add(neighbor);
                    BlockState neighborState = level.getBlockState(neighbor);
                    if (neighborState.is(target.getBlock())
                        && canToolMine(tool, neighborState)) {
                        queue.add(neighbor);
                    }
                }
            }
        }

        return result;
    }

    /**
     * Check if the tool can actually mine the block in survival mode.
     * Mirrors Minecraft's own logic in BlockBehaviour.getDestroyProgress():
     *   1. If block requires correct tool, player must hold the correct tool.
     *   2. If block does not require correct tool, any item/empty hand is fine.
     */
    private static boolean canToolMine(ItemStack tool, BlockState state) {
        if (!state.requiresCorrectToolForDrops()) {
            return true;
        }
        return tool.isCorrectToolForDrops(state);
    }

    private void checkDependencies() {
        FabricLoader loader = FabricLoader.getInstance();
        String[][] required = {
            {"fabric-api",              "0.153.0"},
            {"fabric-key-mapping-api-v1", "2.0.5"},
            {"fabric-events-interaction-v0", "5.2.6"},
            {"fabric-networking-api-v1", "6.3.3"},
        };
        for (String[] req : required) {
            String id = req[0];
            String minVer = req[1];
            loader.getModContainer(id).ifPresentOrElse(container -> {
                ModMetadata meta = container.getMetadata();
                try {
                    Version actual = meta.getVersion();
                    Version min = Version.parse(minVer);
                    if (actual.compareTo(min) < 0) {
                        throw new RuntimeException(
                            "\n========================================\n" +
                            "[ChainMining] 依赖版本不满足！\n" +
                            "  模块: " + id + "\n" +
                            "  需要: >= " + minVer + "\n" +
                            "  当前: " + actual.getFriendlyString() + "\n" +
                            "========================================"
                        );
                    }
                } catch (VersionParsingException e) {
                    throw new RuntimeException("[ChainMining] 无法解析版本号: " + id, e);
                }
            }, () -> {
                throw new RuntimeException(
                    "\n========================================\n" +
                    "[ChainMining] 缺少必要依赖！\n" +
                    "  缺失模块: " + id + "\n" +
                    "  需要版本: >= " + minVer + "\n" +
                    "========================================"
                );
            });
        }
    }
}