package com.chainmining;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.KeyMapping.Category;

public class ChainMiningClient implements ClientModInitializer {
    public static KeyMapping CHAIN_MINE_KEY;

    @Override
    public void onInitializeClient() {
        // Register the key binding — shows up in Settings > Controls
        CHAIN_MINE_KEY = new KeyMapping(
            "key.chain-mining.chain_mine",
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_V,
            Category.GAMEPLAY
        );
        KeyMappingHelper.registerKeyMapping(CHAIN_MINE_KEY);

        // Every client tick: only send when V is held AND attack key is held
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;

            boolean vDown = CHAIN_MINE_KEY.isDown();
            boolean attackDown = client.options.keyAttack.isDown();
            boolean active = vDown && attackDown;

            if (ClientPlayNetworking.canSend(ChainMiningPacket.TYPE)) {
                ClientPlayNetworking.send(new ChainMiningPacket(active));
            }
        });
    }
}