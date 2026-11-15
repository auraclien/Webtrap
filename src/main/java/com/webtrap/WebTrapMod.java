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
    private boolean wasBPressed = false;

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.world == null || client.interactionManager == null) return;
            if (client.currentScreen != null) return;

            // --- ФУНКЦИЯ ПАУТИНЫ (Клавиша V) ---
            if (GLFW.glfwGetKey(client.getWindow().getHandle(), GLFW.GLFW_KEY_V) == GLFW.GLFW_PRESS) {
                if (!wasVPressed) {
                    wasVPressed = true;
                    placeWeb(client);
                }
            } else {
                wasVPressed = false;
            }

            // --- ФУНКЦИЯ АВТО-КОПАНИЯ (Клавиша B) ---
            if (GLFW.glfwGetKey(client.getWindow().getHandle(), GLFW.GLFW_KEY_B) == GLFW.GLFW_PRESS) {
                if (!wasBPressed) {
                    wasBPressed = true;
                    startAutoDig(client);
                }
            } else {
                wasBPressed = false;
            }
        });
    }

    // --- ЛОГИКА ПАУТИНЫ ---
    private void placeWeb(MinecraftClient client) {
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

        LivingEntity target = findClosestTarget(client, 4.0);
        if (target == null) {
            client.player.sendMessage(net.minecraft.text.Text.literal("§cЦель не найдена!"), true);
            return;
        }

        int oldSlot = client.player.getInventory().selectedSlot;
        client.player.getInventory().selectedSlot = webSlot;

        BlockPos targetPos = target.getBlockPos();
        if (client.world.getBlockState(targetPos).isAir()) {
            BlockHitResult hitResult = new BlockHitResult(target.getPos(), Direction.UP, targetPos, false);
            client.interactionManager.interactBlock(client.player, Hand.MAIN_HAND, hitResult);
            client.player.swingHand(Hand.MAIN_HAND);
        }

        client.player.getInventory().selectedSlot = oldSlot;
    }

    // --- ЛОГИКА АВТО-КОПАНИЯ (AutoDig) ---
    private void startAutoDig(MinecraftClient client) {
        // 1. Ищем кирку в хотбаре
        int pickaxeSlot = -1;
        for (int i = 0; i < 9; i++) {
            String itemName = client.player.getInventory().getStack(i).getItem().getTranslationKey();
            if (itemName.contains("pickaxe")) {
                pickaxeSlot = i;
                break;
            }
        }

        if (pickaxeSlot == -1) {
            client.player.sendMessage(net.minecraft.text.Text.literal("§cНет кирки в хотбаре!"), true);
            return;
        }

        // 2. Ищем цель в радиусе 3 блоков
        LivingEntity target = findClosestTarget(client, 3.0);
        if (target == null) {
            client.player.sendMessage(net.minecraft.text.Text.literal("§cЦель не найдена!"), true);
            return;
        }

        // 3. Достаем кирку
        int oldSlot = client.player.getInventory().selectedSlot;
        client.player.getInventory().selectedSlot = pickaxeSlot;

        // 4. Ломаем блок под ногами цели
        BlockPos targetPos = target.getBlockPos().down();
        
        // Отправляем пакет на разрушение блока (мгновенно, как в креативе)
        client.interactionManager.breakBlock(targetPos);
        client.player.swingHand(Hand.MAIN_HAND);

        // 5. Пытаемся поставить паутину туда же (если она есть)
        client.player.sendMessage(net.minecraft.text.Text.literal("§aКопаем под целью!"), true);

        // Возвращаем слот
        client.player.getInventory().selectedSlot = oldSlot;
    }

    // --- Вспомогательный поиск цели ---
    private LivingEntity findClosestTarget(MinecraftClient client, double range) {
        LivingEntity target = null;
        double closestDist = range;
        for (Entity entity : client.world.getEntities()) {
            if (entity instanceof LivingEntity le && entity != client.player && le.isAlive() && !(entity instanceof PlayerEntity && le.isSpectator())) {
                double dist = client.player.distanceTo(entity);
                if (dist < closestDist) {
                    closestDist = dist;
                    target = le;
                }
            }
        }
        return target;
    }
}
