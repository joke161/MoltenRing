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
			// Also check slot 2
			ringStack = ModKeyMappings.getEquippedRing(player, 1);
		}

		List<ElementType> queue = ClientSpellQueue.getQueue();
		RingData ringData = ringStack.isEmpty() ? null : ringStack.getOrDefault(ModDataComponents.RING_DATA, RingData.DEFAULT);

		boolean isDamaged = ringData != null && (ringData.penaltyCooldown() > 0 || ringData.energy() < RingData.MAX_ENERGY);
		boolean hasQueue = !queue.isEmpty();

		// Consensus requirement: only visible when queue is NOT empty OR ring is damaged/regenerating
		if (!hasQueue && !isDamaged) {
			return;
		}

		int screenWidth = guiGraphics.guiWidth();
		int screenHeight = guiGraphics.guiHeight();

		int panelWidth = 168;
		int panelHeight = 36;
		int x = (screenWidth - panelWidth) / 2;
		int y = screenHeight - 68; // Positioned comfortably above health/hotbar

		boolean isOverheating = queue.size() >= 4;

		// 1. Panel Background
		guiGraphics.fill(x, y, x + panelWidth, y + panelHeight, 0xB0111116);

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

		if (!ringStack.isEmpty()) {
			guiGraphics.renderItem(ringStack, ringBoxX + 6, ringBoxY + 6);
			guiGraphics.renderItemDecorations(client.font, ringStack, ringBoxX + 6, ringBoxY + 6);
		}

		// 4. Spell Title / Info above rune sockets
		Font font = client.font;
		int textX = x + 38;
		int textY = y + 5;

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
				guiGraphics.drawString(font, "§6Магма: §e" + ringData.getEnergyPercent() + "%", textX, textY, 0xFFEEDD88, true);
			}
		}

		// 5. 5 Rune Sockets
		int slotStartX = x + 38;
		int slotY = y + 18;
		int slotWidth = 23;
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
	}
}
