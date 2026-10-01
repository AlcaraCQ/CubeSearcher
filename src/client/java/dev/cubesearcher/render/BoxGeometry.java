package dev.cubesearcher.render;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.world.phys.AABB;

/**
 * 直接往 {@link VertexConsumer} 写顶点的几何工具。
 *
 * <p>顶点写法与 Fabric API 自带测试模组的调试几何保持一致，
 * 配合 {@code RenderTypes.debugFilledBox()} 使用。</p>
 */
public final class BoxGeometry {
	private BoxGeometry() {
	}

	/** 画一个实心长方体（6 个面，每面 4 个顶点）。 */
	public static void drawFilledBox(PoseStack.Pose pose, VertexConsumer consumer, AABB box, int color) {
		final float minX = (float) box.minX;
		final float minY = (float) box.minY;
		final float minZ = (float) box.minZ;
		final float maxX = (float) box.maxX;
		final float maxY = (float) box.maxY;
		final float maxZ = (float) box.maxZ;

		// 前（-Z）
		consumer.addVertex(pose, minX, minY, minZ).setColor(color);
		consumer.addVertex(pose, maxX, minY, minZ).setColor(color);
		consumer.addVertex(pose, maxX, maxY, minZ).setColor(color);
		consumer.addVertex(pose, minX, maxY, minZ).setColor(color);
		// 后（+Z）
		consumer.addVertex(pose, maxX, minY, maxZ).setColor(color);
		consumer.addVertex(pose, minX, minY, maxZ).setColor(color);
		consumer.addVertex(pose, minX, maxY, maxZ).setColor(color);
		consumer.addVertex(pose, maxX, maxY, maxZ).setColor(color);
		// 左（-X）
		consumer.addVertex(pose, minX, minY, maxZ).setColor(color);
		consumer.addVertex(pose, minX, minY, minZ).setColor(color);
		consumer.addVertex(pose, minX, maxY, minZ).setColor(color);
		consumer.addVertex(pose, minX, maxY, maxZ).setColor(color);
		// 右（+X）
		consumer.addVertex(pose, maxX, minY, minZ).setColor(color);
		consumer.addVertex(pose, maxX, minY, maxZ).setColor(color);
		consumer.addVertex(pose, maxX, maxY, maxZ).setColor(color);
		consumer.addVertex(pose, maxX, maxY, minZ).setColor(color);
		// 上（+Y）
		consumer.addVertex(pose, minX, maxY, minZ).setColor(color);
		consumer.addVertex(pose, maxX, maxY, minZ).setColor(color);
		consumer.addVertex(pose, maxX, maxY, maxZ).setColor(color);
		consumer.addVertex(pose, minX, maxY, maxZ).setColor(color);
		// 下（-Y）
		consumer.addVertex(pose, minX, minY, maxZ).setColor(color);
		consumer.addVertex(pose, maxX, minY, maxZ).setColor(color);
		consumer.addVertex(pose, maxX, minY, minZ).setColor(color);
		consumer.addVertex(pose, minX, minY, minZ).setColor(color);
	}

	/**
	 * 把包围盒的 12 条棱变成 12 个细长的长方体。
	 *
	 * <p>高亮用的是同一个「实心盒子」管线，这样不必依赖线条管线就能画出清晰的轮廓。</p>
	 *
	 * @param halfThickness 棱的半径（格）
	 */
	public static List<AABB> boxEdges(AABB box, double halfThickness) {
		final double[] xs = {box.minX, box.maxX};
		final double[] ys = {box.minY, box.maxY};
		final double[] zs = {box.minZ, box.maxZ};

		List<AABB> edges = new ArrayList<>(12);

		// 4 条沿 X 轴的棱
		for (double y : ys) {
			for (double z : zs) {
				edges.add(new AABB(
						box.minX, y - halfThickness, z - halfThickness,
						box.maxX, y + halfThickness, z + halfThickness));
			}
		}

		// 4 条沿 Y 轴的棱
		for (double x : xs) {
			for (double z : zs) {
				edges.add(new AABB(
						x - halfThickness, box.minY, z - halfThickness,
						x + halfThickness, box.maxY, z + halfThickness));
			}
		}

		// 4 条沿 Z 轴的棱
		for (double x : xs) {
			for (double y : ys) {
				edges.add(new AABB(
						x - halfThickness, y - halfThickness, box.minZ,
						x + halfThickness, y + halfThickness, box.maxZ));
			}
		}

		return edges;
	}
}
