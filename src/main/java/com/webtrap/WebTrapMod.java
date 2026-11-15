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
    
    private boolean isDigging = false;
    private LivingEntity digTarget = null;
    private int blocksDug = 0;
    private int digDelay = 0;
    private static final int MAX_DIG_DEPTH = 5;

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.world == null || client.interactionManager == null) return;
            if (client.currentScreen != null) return;

            // --- ПАУТИНА (Клавиша V) ---
            if (GLFW.glfwGetKey(client.getWindow().getHandle(), GLFW.GLFW_KEY_V) == GLFW.GLFW_PRESS) {
                if (!wasVPressed) {
                    wasVPressed = true;
                    placeWeb(client);
                }
            } else {
                wasVPressed = false;
            }

            // --- АВТО-КОПКА (Клавиша B) ---
            if (GLFW.glfwGetKey(client.getWindow().getHandle(), GLFW.GLFW_KEY_B) == GLFW.GLFW_PRESS) {
                if (!wasBPressed) {
                    wasBPressed = true;
                    
                    if (isDigging) {
                        isDigging = false;
                        client.player.sendMessage(net.minecraft.text.Text.literal("§cКопка отменена"), true);
                    } else {
                        LivingEntity target = findClosestTarget(client, 4.0);
                        if (target != null) {
                            digTarget = target;
                            blocksDug = 0;
                            digDelay = 0;
                            isDigging = true;
                            client.player.sendMessage(net.minecraft.text.Text.literal("§aКопаем " + target.getName().getString() + " вниз!"), true);
                        } else {
                            client.player.sendMessage(net.minecraft.text.Text.literal("§cЦель не найдена рядом!"), true);
                        }
                    }
                }
            } else {
                wasBPressed = false;
            }

            // --- ЛОГИКА КОПКИ ---
            if (isDigging) {
                doAutoDig(client);
            }
        });
    }

    private void doAutoDig(MinecraftClient client) {
        if (digTarget == null || !digTarget.isAlive() || client.player.distanceTo(digTarget) > 5.0) {
            isDigging = false;
            client.player.sendMessage(net.minecraft.text.Text.literal("§cЦель потеряна!"), true);
            return;
        }

        if (digDelay > 0) {
            digDelay--;
            return;
        }

        // Ищем БУР (кирку) в хотбаре
        int pickaxeSlot = -1;
        for (int i = 0; i < 9; i++) {
            String itemName = client.player.getInventory().getStack(i).getItem().getTranslationKey();
            // Проверяем, что это кирка (можно добавить проверку на зачарование, но пока ищем любую)
            if (itemName.contains("pickaxe")) {
                pickaxeSlot = i;
                break;
            }
        }
        
        if (pickaxeSlot == -1) {
            client.player.sendMessage(net.minecraft.text.Text.literal("§cНет кирки в хотбаре!"), true);
            isDigging = false;
            return;
        }
        client.player.getInventory().selectedSlot = pickaxeSlot;

        // Блок под мобом
        BlockPos targetPos = digTarget.getBlockPos().down();

        // Если блок воздух (моб падает) - ждем
        if (client.world.getBlockState(targetPos).isAir()) {
            digDelay = 2; 
            return;
        }

        // ЛОМАЕМ БЛОК (имитируем удар)
        client.interactionManager.updateBlockBreakingProgress(targetPos, Direction.UP);
        client.player.swingHand(Hand.MAIN_HAND);
        
        blocksDug++;
        digDelay = 2; // Задержка, чтобы моб упал

        // Если прокопали 5 блоков - ставим паутину
        if (blocksDug >= MAX_DIG_DEPTH) {
            int webSlot = -1;
            for (int i = 0; i < 9; i++) {
                if (client.player.getInventory().getStack(i).getItem() == Items.COBWEB) {
                    webSlot = i;
                    break;
                }
            }
            
            if (webSlot != -1) {
                client.player.getInventory().selectedSlot = webSlot;
                // Ставим паутину туда, где сейчас моб (то есть на дно ямы)
                client.interactionManager.interactBlock(client.player, Hand.MAIN_HAND, 
                    new BlockHitResult(digTarget.getPos(), Direction.UP, digTarget.getBlockPos().down(), false));
                client.player.swingHand(Hand.MAIN_HAND);
                client.player.sendMessage(net.minecraft.text.Text.literal("§aГотово! Моб в паутине."), true);
            } else {
                client.player.sendMessage(net.minecraft.text.Text.literal("§cНет паутины!"), true);
            }
            
            isDigging = false;
            digTarget = null;
            blocksDug = 0;
        }
    }

    private void placeWeb(MinecraftClient client) {
        int webSlot = -1;
        for (int i = 0; i < 9; i++) {
            if (client.player.getInventory().getStack(i).getItem() == Items.COBWEB) {
                webSlot = i;
                break;
            }
        }

        if (webSlot == -1) {
            client.player.sendMessage(net.minecraft.text.Text.literal("§cНет паутины!"), true);
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
