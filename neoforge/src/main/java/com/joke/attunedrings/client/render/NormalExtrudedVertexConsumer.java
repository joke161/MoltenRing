package com.joke.attunedrings.client.render;

import com.mojang.blaze3d.vertex.VertexConsumer;

/**
 * Lightweight VertexConsumer decorator that extrudes each vertex strictly outward along its
 * surface normal so magma vein overlays sit cleanly above skin and outer clothing layers.
 * <p>
 * Supports two modes:
 * <ul>
 *   <li>{@code useTriangleWinding = true} (Epic Fight {@code TRIANGLES}): buffers 3 vertices per triangle
 *       and derives the true world-space outward normal via CCW cross-product {@code (v1 - v0) x (v2 - v0)},
 *       making extrusion immune to unrotated pose normal matrices during {@code yBodyRot} / strafing.</li>
 *   <li>{@code useTriangleWinding = false} (Vanilla {@code ModelPart}): extrudes directly along the
 *       submitted vertex normal for both 11-argument {@code addVertex} and streamed vertices.</li>
 * </ul>
 */
public final class NormalExtrudedVertexConsumer implements VertexConsumer {
	public static final float THIRD_PERSON_OFFSET = 0.020F;
	public static final float FIRST_PERSON_OFFSET = 0.0035F;

	private static final class VertexSlot {
		float x, y, z;
		boolean hasColor;
		int r, g, b, a;
		boolean hasUv;
		float u, v;
		boolean hasUv1;
		int u1, v1;
		boolean hasUv2;
		int u2, v2;
		float nx, ny, nz;

		void resetPos(float x, float y, float z) {
			this.x = x;
			this.y = y;
			this.z = z;
			this.hasColor = false;
			this.hasUv = false;
			this.hasUv1 = false;
			this.hasUv2 = false;
			this.nx = 0.0F;
			this.ny = 0.0F;
			this.nz = 0.0F;
		}
	}

	private final VertexConsumer delegate;
	private final float offset;
	private final boolean useTriangleWinding;

	private final VertexSlot[] triSlots = { new VertexSlot(), new VertexSlot(), new VertexSlot() };
	private int triCount = 0;
	private boolean hasPending = false;

	public NormalExtrudedVertexConsumer(VertexConsumer delegate) {
		this(delegate, THIRD_PERSON_OFFSET, false);
	}

	public NormalExtrudedVertexConsumer(VertexConsumer delegate, float offset) {
		this(delegate, offset, false);
	}

	public NormalExtrudedVertexConsumer(VertexConsumer delegate, float offset, boolean useTriangleWinding) {
		this.delegate = delegate;
		this.offset = offset;
		this.useTriangleWinding = useTriangleWinding;
	}

	private VertexSlot activeSlot() {
		return this.triSlots[this.triCount];
	}

	private static float invLengthOrZero(float x, float y, float z, float minLenSq) {
		float lenSq = x * x + y * y + z * z;
		return lenSq > minLenSq ? (float) (1.0 / Math.sqrt(lenSq)) : 0.0F;
	}

	private void emitSlot(VertexSlot s, float ox, float oy, float oz, float nx, float ny, float nz) {
		// Emit in strict DefaultVertexFormat.NEW_ENTITY order: Position -> Color -> UV0 -> UV1 -> UV2 -> Normal
		this.delegate.addVertex(s.x + ox, s.y + oy, s.z + oz);
		if (s.hasColor) {
			this.delegate.setColor(s.r, s.g, s.b, s.a);
		}
		if (s.hasUv) {
			this.delegate.setUv(s.u, s.v);
		}
		if (s.hasUv1) {
			this.delegate.setUv1(s.u1, s.v1);
		}
		if (s.hasUv2) {
			this.delegate.setUv2(s.u2, s.v2);
		}
		this.delegate.setNormal(nx, ny, nz);
	}

	private void flushTriangleWithWinding() {
		VertexSlot v0 = this.triSlots[0];
		VertexSlot v1 = this.triSlots[1];
		VertexSlot v2 = this.triSlots[2];

		float e1x = v1.x - v0.x;
		float e1y = v1.y - v0.y;
		float e1z = v1.z - v0.z;
		float e2x = v2.x - v0.x;
		float e2y = v2.y - v0.y;
		float e2z = v2.z - v0.z;

		float wx = e1y * e2z - e1z * e2y;
		float wy = e1z * e2x - e1x * e2z;
		float wz = e1x * e2y - e1y * e2x;

		float invLen = invLengthOrZero(wx, wy, wz, 1.0E-12F);
		float fnx;
		float fny;
		float fnz;
		if (invLen > 0.0F) {
			fnx = wx * invLen;
			fny = wy * invLen;
			fnz = wz * invLen;
		} else {
			float nInv = invLengthOrZero(v0.nx, v0.ny, v0.nz, 1.0E-6F);
			fnx = v0.nx * nInv;
			fny = v0.ny * nInv;
			fnz = v0.nz * nInv;
		}

		float ox = fnx * this.offset;
		float oy = fny * this.offset;
		float oz = fnz * this.offset;

		emitSlot(v0, ox, oy, oz, fnx, fny, fnz);
		emitSlot(v1, ox, oy, oz, fnx, fny, fnz);
		emitSlot(v2, ox, oy, oz, fnx, fny, fnz);
		this.triCount = 0;
	}

	private void flushUnextrudedSlot(VertexSlot s) {
		emitSlot(s, 0.0F, 0.0F, 0.0F, s.nx, s.ny, s.nz);
	}

	@Override
	public VertexConsumer addVertex(float x, float y, float z) {
		if (this.hasPending) {
			flushUnextrudedSlot(activeSlot());
			this.hasPending = false;
			if (!this.useTriangleWinding) {
				this.triCount = 0;
			}
		}
		activeSlot().resetPos(x, y, z);
		this.hasPending = true;
		return this;
	}

	@Override
	public VertexConsumer setColor(int red, int green, int blue, int alpha) {
		if (this.hasPending) {
			VertexSlot s = activeSlot();
			s.r = red;
			s.g = green;
			s.b = blue;
			s.a = alpha;
			s.hasColor = true;
		} else {
			this.delegate.setColor(red, green, blue, alpha);
		}
		return this;
	}

	@Override
	public VertexConsumer setUv(float u, float v) {
		if (this.hasPending) {
			VertexSlot s = activeSlot();
			s.u = u;
			s.v = v;
			s.hasUv = true;
		} else {
			this.delegate.setUv(u, v);
		}
		return this;
	}

	@Override
	public VertexConsumer setUv1(int u, int v) {
		if (this.hasPending) {
			VertexSlot s = activeSlot();
			s.u1 = u;
			s.v1 = v;
			s.hasUv1 = true;
		} else {
			this.delegate.setUv1(u, v);
		}
		return this;
	}

	@Override
	public VertexConsumer setUv2(int u, int v) {
		if (this.hasPending) {
			VertexSlot s = activeSlot();
			s.u2 = u;
			s.v2 = v;
			s.hasUv2 = true;
		} else {
			this.delegate.setUv2(u, v);
		}
		return this;
	}

	@Override
	public VertexConsumer setNormal(float normalX, float normalY, float normalZ) {
		if (this.hasPending) {
			VertexSlot s = activeSlot();
			s.nx = normalX;
			s.ny = normalY;
			s.nz = normalZ;
			this.hasPending = false;

			if (this.useTriangleWinding) {
				this.triCount++;
				if (this.triCount == 3) {
					flushTriangleWithWinding();
				}
			} else {
				float scale = invLengthOrZero(normalX, normalY, normalZ, 1.0E-6F) * this.offset;
				emitSlot(s, normalX * scale, normalY * scale, normalZ * scale, normalX, normalY, normalZ);
			}
		} else {
			this.delegate.setNormal(normalX, normalY, normalZ);
		}
		return this;
	}

	@Override
	public void addVertex(float x, float y, float z, int color, float u, float v,
						  int packedOverlay, int packedLight,
						  float normalX, float normalY, float normalZ) {
		if (this.hasPending) {
			flushUnextrudedSlot(activeSlot());
			this.hasPending = false;
			this.triCount = 0;
		}
		float scale = invLengthOrZero(normalX, normalY, normalZ, 1.0E-6F) * this.offset;
		this.delegate.addVertex(
			x + normalX * scale,
			y + normalY * scale,
			z + normalZ * scale,
			color, u, v, packedOverlay, packedLight, normalX, normalY, normalZ
		);
	}
}
