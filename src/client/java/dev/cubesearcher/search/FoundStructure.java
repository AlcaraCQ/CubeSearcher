package dev.cubesearcher.search;

import net.minecraft.world.phys.AABB;

/**
 * 一次成功匹配到的结构。
 *
 * @param minX  最小角（包含）的 X
 * @param minY  最小角（包含）的 Y
 * @param minZ  最小角（包含）的 Z
 * @param sizeX X 方向格数
 * @param sizeY Y 方向格数
 * @param sizeZ Z 方向格数
 */
public record FoundStructure(int minX, int minY, int minZ, int sizeX, int sizeY, int sizeZ) {
	/** 结构占用的方块数。 */
	public int blockCount() {
		return sizeX * sizeY * sizeZ;
	}

	/** 渲染用的包围盒（世界坐标，1 格 = 1 单位）。 */
	public AABB toBox() {
		return new AABB(minX, minY, minZ, minX + sizeX, minY + sizeY, minZ + sizeZ);
	}
}
