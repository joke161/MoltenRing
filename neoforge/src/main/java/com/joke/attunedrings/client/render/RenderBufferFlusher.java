package com.joke.attunedrings.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Commits dynamic overlay RenderTypes immediately after draw calls, handling both vanilla
 * {@link MultiBufferSource.BufferSource} and shader/mod wrappers such as Iris's BufferSourceWrapper.
 */
public final class RenderBufferFlusher {
	private record EndBatchMethods(Method endBatchTyped, Method endBatchNoArg) {
	}

	private static final EndBatchMethods NONE = new EndBatchMethods(null, null);
	private static final Map<Class<?>, EndBatchMethods> METHOD_CACHE = new ConcurrentHashMap<>();

	private RenderBufferFlusher() {
	}

	public static void flush(MultiBufferSource bufferSource, RenderType renderType) {
		if (bufferSource == null) {
			return;
		}
		if (bufferSource instanceof MultiBufferSource.BufferSource bs) {
			bs.endBatch(renderType);
			return;
		}

		EndBatchMethods cached = METHOD_CACHE.computeIfAbsent(bufferSource.getClass(), RenderBufferFlusher::resolveMethods);
		try {
			if (cached.endBatchTyped() != null) {
				cached.endBatchTyped().invoke(bufferSource, renderType);
			} else if (cached.endBatchNoArg() != null) {
				cached.endBatchNoArg().invoke(bufferSource);
			}
		} catch (Throwable ignored) {
			// Fall back to committing the primary RenderBuffers BufferSource below
		}

		Minecraft.getInstance().renderBuffers().bufferSource().endBatch(renderType);
	}

	private static EndBatchMethods resolveMethods(Class<?> clazz) {
		Method typed = findMethod(clazz, "endBatch", RenderType.class);
		Method noArg = typed != null ? null : findMethod(clazz, "endBatch");
		return (typed != null || noArg != null) ? new EndBatchMethods(typed, noArg) : NONE;
	}

	private static Method findMethod(Class<?> clazz, String name, Class<?>... params) {
		for (Class<?> c = clazz; c != null; c = c.getSuperclass()) {
			try {
				Method m = c.getDeclaredMethod(name, params);
				m.setAccessible(true);
				return m;
			} catch (NoSuchMethodException ignored) {
			}
		}
		try {
			Method m = clazz.getMethod(name, params);
			m.setAccessible(true);
			return m;
		} catch (NoSuchMethodException ignored) {
			return null;
		}
	}
}

