package com.webtrap;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.lwjgl.glfw.GLFW;

public class WebTrapMod implements ClientModInitializer {
    private boolean wasVPressed = false;

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.world == null || client.interactionManager == null) return;
            if (client.currentScreen != null) return;

            // Слушаем нажатие клавиши V
            if (GLFW.glfwGetKey(client.getWindow().getHandle(), GLFW.GLFW_KEY_V) == GLFW.GLFW_PRESS) {
                if (!wasVPressed) {
                    wasVPressed = true;
                    placeWeb(client);
                }
            } else {
                wasVPressed = false;
            }
        });
    }

    private void placeWeb(MinecraftClient client) {
        // 1. Ищем паутину в хотбаре (слоты 0-8)
        int webSlot = -1;
        for (int i = 0; i < 9; i++) {
            if (client.player.getInventory().getStack(i).getItem() == Items.COBWEB) {
                webSlot = i;
                break;
            }
        }

        if (webSlot == -1) {
            client.player.sendMessage(net.minecraft.text.Text.literal("§cНет паутины в хотбаре!"), true);
            return;
        }

        // 2. Ищем ближайшую цель (игрока или моба) в радиусе 4 блоков
        LivingEntity target = null;
        double closestDist = 4.0;
        for (Entity entity : client.world.getEntities()) {
            if (entity instanceof LivingEntity le && entity != client.player && le.isAlive() && !(entity instanceof PlayerEntity && le.isSpectator())) {
                double dist = client.player.distanceTo(entity);
                if (dist < closestDist) {
                    closestDist = dist;
                    target = le;
                }
            }
        }

        if (target == null) {
            client.player.sendMessage(net.minecraft.text.Text.literal("§cЦель не найдена!"), true);
            return;
        }

        // 3. Ставим паутину под ноги цели
        int oldSlot = client.player.getInventory().selectedSlot;
        client.player.getInventory().selectedSlot = webSlot;

        BlockPos targetPos = target.getBlockPos();

        // Проверяем, что там воздух (чтобы не сломать блок)
        if (client.world.getBlockState(targetPos).isAir()) {
            BlockHitResult hitResult = new BlockHitResult(
                target.getPos(), 
                Direction.UP, 
                targetPos, 
                false
            );
            client.interactionManager.interactBlock(client.player, Hand.MAIN_HAND, hitResult);
            client.player.swingHand(Hand.MAIN_HAND);
        }

        // Возвращаем старый слот
        client.player.getInventory().selectedSlot = oldSlot;
    }
}
