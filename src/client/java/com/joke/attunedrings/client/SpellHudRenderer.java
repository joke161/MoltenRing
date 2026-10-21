package com.joke.attunedrings.client;

import com.joke.attunedrings.component.RingData;
import com.joke.attunedrings.magic.ElementType;
import com.joke.attunedrings.magic.SpellType;
import com.joke.attunedrings.registry.ModDataComponents;
import com.joke.attunedrings.registry.ModItems;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;

public class SpellHudRenderer {

	public static void init() {
		HudRenderCallback.EVENT.register(SpellHudRenderer::renderHud);
	}

	private static void renderHud(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.options.hideGui) {
			return;
		}

		Player player = client.player;
		ItemStack ringStack = ModKeyMappings.getEquippedRing(player, 0);
		if (ringStack.isEmpty() || !ringStack.is(ModItems.MOLTEN_RING)) {
			ItemStack slot2 = ModKeyMappings.getEquippedRing(player, 1);
			if (slot2.is(ModItems.MOLTEN_RING)) {
				ringStack = slot2;
			}
		}

		// Also check held items if not in Trinkets
		if (ringStack.isEmpty() || !ringStack.is(ModItems.MOLTEN_RING)) {
			if (player.getMainHandItem().is(ModItems.MOLTEN_RING)) {
				ringStack = player.getMainHandItem();
			} else if (player.getOffhandItem().is(ModItems.MOLTEN_RING)) {
				ringStack = player.getOffhandItem();
			}
		}

		// Check for unattuned ring if no molten ring found
		boolean isUnattuned = false;
		if (ringStack.isEmpty()) {
			ItemStack u1 = ModKeyMappings.getEquippedRing(player, 0);
			ItemStack u2 = ModKeyMappings.getEquippedRing(player, 1);
			if (u1.is(ModItems.UNATTUNED_RING)) {
				ringStack = u1;
				isUnattuned = true;
			} else if (u2.is(ModItems.UNATTUNED_RING)) {
				ringStack = u2;
				isUnattuned = true;
			} else if (player.getMainHandItem().is(ModItems.UNATTUNED_RING)) {
				ringStack = player.getMainHandItem();
				isUnattuned = true;
			} else if (player.getOffhandItem().is(ModItems.UNATTUNED_RING)) {
				ringStack = player.getOffhandItem();
				isUnattuned = true;
			}
		}

		if (ringStack.isEmpty()) {
			return; // Don't show HUD if player has neither ring
		}

		List<ElementType> queue = ClientSpellQueue.getQueue();
		RingData ringData = isUnattuned ? null : ringStack.getOrDefault(ModDataComponents.RING_DATA, RingData.DEFAULT);

		int screenWidth = guiGraphics.guiWidth();
		int screenHeight = guiGraphics.guiHeight();

		int panelWidth = 176;
		int panelHeight = 36;
		int x = (screenWidth - panelWidth) / 2;
		int y = screenHeight - 74; // Positioned comfortably above health & hotbar

		boolean isOverheating = queue.size() >= 4;

		// 1. Panel Background
		guiGraphics.fill(x, y, x + panelWidth, y + panelHeight, 0xB8101016);

		// 2. Border Outline (Pulsing fiery heat when 4-5 stacks)
		int borderColor = 0xFF4A4A5A;
		if (isOverheating) {
			float pulse = (float) (0.5 + 0.5 * Math.sin(System.currentTimeMillis() * 0.009));
			int r = 255;
			int g = (int) (60 + pulse * 120);
			int b = 0;
			borderColor = (0xFF << 24) | (r << 16) | (g << 8) | b;
			guiGraphics.renderOutline(x - 1, y - 1, panelWidth + 2, panelHeight + 2, borderColor);
		} else {
			guiGraphics.renderOutline(x, y, panelWidth, panelHeight, borderColor);
		}

		// 3. Ring Frame & Icon on left side
		int ringBoxX = x + 4;
		int ringBoxY = y + 4;
		int ringBoxSize = 28;
		guiGraphics.fill(ringBoxX, ringBoxY, ringBoxX + ringBoxSize, ringBoxY + ringBoxSize, 0x801A1A22);
		guiGraphics.renderOutline(ringBoxX, ringBoxY, ringBoxSize, ringBoxSize, isOverheating ? borderColor : 0xFF3A3A48);

		guiGraphics.renderItem(ringStack, ringBoxX + 6, ringBoxY + 6);
		guiGraphics.renderItemDecorations(client.font, ringStack, ringBoxX + 6, ringBoxY + 6);

		Font font = client.font;
		int textX = x + 38;
		int textY = y + 5;

		String key1 = ModKeyMappings.KEY_RING_1.getTranslatedKeyMessage().getString();
		String keyCast = ModKeyMappings.KEY_CAST.getTranslatedKeyMessage().getString();

		if (isUnattuned) {
			int heat = ringStack.getOrDefault(ModDataComponents.HEAT_PROGRESS, 0);
			int pct = Math.min(100, (int) Math.round(((double) heat / 200.0) * 100.0));
			if (heat > 0) {
				guiGraphics.drawString(font, "§6Закалка: §e" + pct + "%", textX, textY, 0xFFFFFFFF, true);
			} else {
				guiGraphics.drawString(font, Component.translatable("gui.attunedrings.hud_unattuned").getString(), textX, textY, 0xFFCCCCCC, true);
			}
			guiGraphics.drawString(font, "§8Огонь: 10с  §8|  §8Лава: 5с", textX, y + 19, 0xFFAAAAAA, true);
			return;
		}

		// 4. Spell Title / Info above rune sockets
		boolean hasQueue = !queue.isEmpty();
		if (hasQueue) {
			Optional<SpellType> spellOpt = SpellType.fromQueue(queue);
			if (spellOpt.isPresent()) {
				SpellType spell = spellOpt.get();
				boolean discovered = ClientSpellQueue.isDiscovered(spell.getId());
				int costPercent = (spell.getEnergyCost() * 100) / RingData.MAX_ENERGY;

				if (discovered) {
					String name = Component.translatable(spell.getTranslationKey()).getString();
					guiGraphics.drawString(font, "§6§l" + name + " §c-" + costPercent + "%", textX, textY, 0xFFFFFFFF, true);
				} else {
					guiGraphics.drawString(font, "§e§k???§r §7(Неизвестно) §c-" + costPercent + "%", textX, textY, 0xFFFFFFFF, true);
				}
			} else {
				guiGraphics.drawString(font, "§cНеизвестная комбинация", textX, textY, 0xFFFFAAAA, true);
			}
		} else if (ringData != null) {
			if (ringData.penaltyCooldown() > 0) {
				int sec = (ringData.penaltyCooldown() + 19) / 20;
				guiGraphics.drawString(font, "§c§lПЕРЕГРЕВ! §7Откат: " + sec + "с", textX, textY, 0xFFFF5555, true);
			} else {
				String readyText = Component.translatable("gui.attunedrings.hud_ready").getString();
				guiGraphics.drawString(font, "§6" + readyText + " §8| §e" + ringData.getEnergyPercent() + "%", textX, textY, 0xFFEEDD88, true);
			}
		}

		// 5. 5 Rune Sockets
		int slotStartX = x + 38;
		int slotY = y + 18;
		int slotWidth = 24;
		int slotHeight = 13;

		for (int i = 0; i < 5; i++) {
			int slotX = slotStartX + (i * (slotWidth + 3));
			boolean filled = i < queue.size();

			if (filled) {
				ElementType elem = queue.get(i);
				// Glowing filled slot
				int fillColor = (isOverheating && (i >= 3)) ? 0xDDFF3300 : 0xDDDD5500;
				guiGraphics.fill(slotX, slotY, slotX + slotWidth, slotY + slotHeight, fillColor);
				guiGraphics.renderOutline(slotX, slotY, slotWidth, slotHeight, 0xFFFFCC44);

				// Centered fire symbol
				String sym = elem.getSymbol();
				int symW = font.width(sym);
				guiGraphics.drawString(font, sym, slotX + (slotWidth - symW) / 2, slotY + 3, 0xFFFFFFFF, true);
			} else {
				// Dim empty socket
				guiGraphics.fill(slotX, slotY, slotX + slotWidth, slotY + slotHeight, 0x40000000);
				guiGraphics.renderOutline(slotX, slotY, slotWidth, slotHeight, 0xFF333340);
			}
		}

		// 6. Key Hint Bar directly below the panel
		String hintText = Component.translatable("gui.attunedrings.hud_hint", key1, keyCast, keyCast).getString();
		int hintW = font.width(hintText);
		int hintX = x + (panelWidth - hintW) / 2;
		int hintY = y + panelHeight + 3;
		guiGraphics.fill(hintX - 3, hintY - 1, hintX + hintW + 3, hintY + 9, 0x90000000);
		guiGraphics.drawString(font, hintText, hintX, hintY, 0xFFE0E0E0, true);
	}
}
