package dev.cubesearcher.render;

import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import dev.cubesearcher.search.FoundStructure;

/**
 * 把找到的结构高亮出来。
 *
 * <p>26.x 的世界渲染改成了「抽取(extraction) / 绘制(drawing)」两段式，
 * Fabric 对应的入口是 {@link LevelRenderEvents}。这里挂在
 * {@link LevelRenderEvents#BEFORE_TRANSLUCENT_TERRAIN}（半透明地形之前）上，
 * 用 {@code RenderTypes.debugFilledBox()} 提交「半透明填充盒 + 12 条棱」的几何。</p>
 */
public final class StructureHighlighter {
	/** 填充的透明度。 */
	private static final float FILL_ALPHA = 0.25f;
	/** 描边棱的半径（格）。 */
	private static final double EDGE_HALF_THICKNESS = 0.02;

	private static final int FILL_COLOR = ARGB.colorFromFloat(FILL_ALPHA, 0.45f, 0.80f, 1.00f);
	private static final int EDGE_COLOR = ARGB.colorFromFloat(1.00f, 0.25f, 0.75f, 1.00f);

	// 绘制阶段在渲染线程上读，搜索在客户端 tick 上写，因此用 volatile 保证可见性。
	// AABB / 不可变 List 都是不可变对象，用 volatile 发布是安全的；
	// 12 条棱的包围盒在命中时算一次，避免每帧重复分配。
	private static volatile ClientLevel highlightedLevel;
	private static volatile AABB highlightedBox;
	private static volatile List<AABB> highlightedEdges = List.of();

	private StructureHighlighter() {
	}

	/** 注册渲染回调，只在客户端初始化时调用一次。 */
	public static void register() {
		LevelRenderEvents.BEFORE_TRANSLUCENT_TERRAIN.register(StructureHighlighter::render);
	}

	/** 当前是否处于「已高亮」状态（按下快捷键时用它决定是搜索还是解除）。 */
	public static boolean hasHighlight() {
		return highlightedBox != null;
	}

	public static void highlight(ClientLevel level, FoundStructure structure) {
		final AABB box = structure.toBox();

		highlightedBox = box;
		highlightedEdges = List.copyOf(BoxGeometry.boxEdges(box, EDGE_HALF_THICKNESS));
		highlightedLevel = level;
	}

	public static void clear() {
		highlightedBox = null;
		highlightedEdges = List.of();
		highlightedLevel = null;
	}

	private static void render(LevelRenderContext context) {
		// 先各自快照一次，保证同一帧用的是同一份数据
		final AABB box = highlightedBox;
		final List<AABB> edges = highlightedEdges;
		final ClientLevel level = highlightedLevel;

		// 只在高亮时所在的那个世界渲染：切换维度/退出世界后自动不再显示
		if (box == null || level == null || Minecraft.getInstance().level != level) {
			return;
		}

		final Vec3 camera = context.levelState().cameraRenderState.pos;
		final PoseStack poseStack = context.poseStack();

		poseStack.pushPose();
		// 这个事件里的坐标原点是世界原点，先平移摄像机的偏移量
		poseStack.translate(-camera.x, -camera.y, -camera.z);

		// 半透明填充
		context.submitNodeCollector().submitCustomGeometry(poseStack, RenderTypes.debugFilledBox(),
				(pose, buffer) -> BoxGeometry.drawFilledBox(pose, buffer, box, FILL_COLOR));

		// 12 条棱，画在填充之后所以会盖在上面（棱的包围盒在 highlight() 时已算好）
		for (AABB edge : edges) {
			context.submitNodeCollector().submitCustomGeometry(poseStack, RenderTypes.debugFilledBox(),
					(pose, buffer) -> BoxGeometry.drawFilledBox(pose, buffer, edge, EDGE_COLOR));
		}

		poseStack.popPose();
	}
}
