package com.joke.attunedrings.magic;

import java.util.List;
import java.util.Optional;

public enum SpellType {
	FIRE_1("fire_1", List.of(ElementType.FIRE), 120, "spell.attunedrings.firebolt"),
	FIRE_2("fire_2", List.of(ElementType.FIRE, ElementType.FIRE), 300, "spell.attunedrings.flame_cone"),
	FIRE_3("fire_3", List.of(ElementType.FIRE, ElementType.FIRE, ElementType.FIRE), 540, "spell.attunedrings.lava_strider"),
	FIRE_4("fire_4", List.of(ElementType.FIRE, ElementType.FIRE, ElementType.FIRE, ElementType.FIRE), 840, "spell.attunedrings.fire_shield"),
	FIRE_5("fire_5", List.of(ElementType.FIRE, ElementType.FIRE, ElementType.FIRE, ElementType.FIRE, ElementType.FIRE), 1200, "spell.attunedrings.supernova");

	private final String id;
	private final List<ElementType> recipe;
	private final int energyCost;
	private final String translationKey;

	SpellType(String id, List<ElementType> recipe, int energyCost, String translationKey) {
		this.id = id;
		this.recipe = recipe;
		this.energyCost = energyCost;
		this.translationKey = translationKey;
	}

	public String getId() {
		return id;
	}

	public List<ElementType> getRecipe() {
		return recipe;
	}

	public int getEnergyCost() {
		return energyCost;
	}

	public String getTranslationKey() {
		return translationKey;
	}

	public static Optional<SpellType> fromQueue(List<ElementType> queue) {
		if (queue == null || queue.isEmpty()) {
			return Optional.empty();
		}
		for (SpellType spell : values()) {
			if (spell.recipe.equals(queue)) {
				return Optional.of(spell);
			}
		}
		return Optional.empty();
	}
}
