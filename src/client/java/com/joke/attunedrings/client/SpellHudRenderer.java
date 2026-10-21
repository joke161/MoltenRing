package com.joke.attunedrings.client;

import com.joke.attunedrings.component.RingData;
import com.joke.attunedrings.magic.ElementType;
import com.joke.attunedrings.magic.SpellType;
import com.joke.attunedrings.registry.ModDataComponents;
import com.joke.attunedrings.registry.ModItems;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class SpellHudRenderer {

	public static void init() {
		HudRenderCallback.EVENT.register(SpellHudRenderer::renderHud);
	}

	public static String getLatinKeyName(KeyMapping keyMapping) {
		try {
			var key = KeyBindingHelper.getBoundKeyOf(keyMapping);
			if (key != null) {
				String name = key.getName();
				if (name != null && !name.isEmpty()) {
					if (name.startsWith("key.keyboard.")) {
						name = name.substring("key.keyboard.".length());
					} else if (name.startsWith("key.mouse.")) {
						name = "M" + name.substring("key.mouse.".length());
					} else if (name.startsWith("key.")) {
						name = name.substring("key.".length());
					}
					return name.toUpperCase(Locale.ROOT);
				}
			}
		} catch (Exception ignored) {
		}
		return keyMapping.getTranslatedKeyMessage().getString().toUpperCase(Locale.ROOT);
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

		int screenHeight = guiGraphics.guiHeight();

		// Unified compact panel positioned in the bottom-left corner
		int panelWidth = 174;
		int panelHeight = 44;
		int x = 6;
		int y = screenHeight - panelHeight - 6;

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
		int ringBoxX = x + 5;
		int ringBoxY = y + 5;
		int ringBoxSize = 26;
		guiGraphics.fill(ringBoxX, ringBoxY, ringBoxX + ringBoxSize, ringBoxY + ringBoxSize, 0x801A1A22);
		guiGraphics.renderOutline(ringBoxX, ringBoxY, ringBoxSize, ringBoxSize, isOverheating ? borderColor : 0xFF3A3A48);

		guiGraphics.renderItem(ringStack, ringBoxX + 5, ringBoxY + 5);
		guiGraphics.renderItemDecorations(client.font, ringStack, ringBoxX + 5, ringBoxY + 5);

		Font font = client.font;
		int textX = x + 36;
		int textY = y + 4;

		String key1 = getLatinKeyName(ModKeyMappings.KEY_RING_1);
		String keyCast = getLatinKeyName(ModKeyMappings.KEY_CAST);

		if (isUnattuned) {
			int heat = ringStack.getOrDefault(ModDataComponents.HEAT_PROGRESS, 0);
			int pct = Math.min(100, (int) Math.round(((double) heat / 200.0) * 100.0));
			if (heat > 0) {
				guiGraphics.drawString(font, Component.translatable("gui.attunedrings.hud_heating", pct).getString(), textX, textY, 0xFFFFFFFF, true);
				// Progress bar across Row 2
				int barX = textX;
				int barY = y + 16;
				int barW = 132;
				int barH = 11;
				guiGraphics.fill(barX, barY, barX + barW, barY + barH, 0x50000000);
				guiGraphics.renderOutline(barX, barY, barW, barH, 0xFF553311);
				int fillW = Math.max(1, Math.round((float) (barW - 2) * ((float) heat / 200.0f)));
				guiGraphics.fill(barX + 1, barY + 1, barX + 1 + fillW, barY + barH - 1, 0xFFFF7700);
			} else {
				guiGraphics.drawString(font, Component.translatable("gui.attunedrings.hud_unattuned").getString(), textX, textY, 0xFFFFFFFF, true);
				guiGraphics.drawString(font, Component.translatable("gui.attunedrings.hud_unattuned_guide").getString(), textX, y + 17, 0xFFCCCCCC, true);
			}
			// Heating hint inside frame at Row 3
			String hint = Component.translatable("gui.attunedrings.hud_heating_hint").getString();
			int hintW = font.width(hint);
			guiGraphics.drawString(font, hint, x + (panelWidth - hintW) / 2, y + 31, 0xFFAAAAAA, true);
			return;
		}

		// 4. Spell Title / Info above rune sockets (Row 1)
		boolean hasQueue = !queue.isEmpty();
		if (hasQueue) {
			Optional<SpellType> spellOpt = SpellType.fromQueue(queue);
			if (spellOpt.isPresent()) {
				SpellType spell = spellOpt.get();
				boolean discovered = ClientSpellQueue.isDiscovered(spell.getId());
				int costPercent = (spell.getEnergyCost() * 100) / RingData.MAX_ENERGY;

				if (discovered) {
					String name = Component.translatable(spell.getTranslationKey()).getString();
					guiGraphics.drawString(font, Component.translatable("gui.attunedrings.hud_spell_cost", name, costPercent).getString(), textX, textY, 0xFFFFFFFF, true);
				} else {
					guiGraphics.drawString(font, Component.translatable("gui.attunedrings.hud_unknown_spell", costPercent).getString(), textX, textY, 0xFFFFFFFF, true);
				}
			} else {
				guiGraphics.drawString(font, Component.translatable("gui.attunedrings.hud_unknown_combo").getString(), textX, textY, 0xFFFFAAAA, true);
			}
		} else if (ringData != null) {
			if (ringData.penaltyCooldown() > 0) {
				int sec = (ringData.penaltyCooldown() + 19) / 20;
				guiGraphics.drawString(font, Component.translatable("gui.attunedrings.hud_overheat", sec).getString(), textX, textY, 0xFFFF5555, true);
			} else {
				guiGraphics.drawString(font, Component.translatable("gui.attunedrings.hud_ready_status", ringData.getEnergyPercent()).getString(), textX, textY, 0xFFEEDD88, true);
			}
		}

		// 5. 5 Rune Sockets (Row 2)
		int slotStartX = x + 36;
		int slotY = y + 16;
		int slotWidth = 23;
		int slotHeight = 12;
		int slotSpacing = 3;

		for (int i = 0; i < 5; i++) {
			int slotX = slotStartX + (i * (slotWidth + slotSpacing));
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
				guiGraphics.drawString(font, sym, slotX + (slotWidth - symW) / 2, slotY + 2, 0xFFFFFFFF, true);
			} else {
				// Dim empty socket
				guiGraphics.fill(slotX, slotY, slotX + slotWidth, slotY + slotHeight, 0x40000000);
				guiGraphics.renderOutline(slotX, slotY, slotWidth, slotHeight, 0xFF333340);
			}
		}

		// 6. Key Hint inside the frame at the bottom (Row 3)
		String hintText = Component.translatable("gui.attunedrings.hud_hint", key1, keyCast, keyCast).getString();
		int hintW = font.width(hintText);
		int hintX = Math.max(x + 4, x + (panelWidth - hintW) / 2);
		guiGraphics.drawString(font, hintText, hintX, y + 31, 0xFFE0E0E0, true);
	}
}
