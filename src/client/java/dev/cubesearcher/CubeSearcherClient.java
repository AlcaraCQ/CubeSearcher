package dev.cubesearcher;

import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import dev.cubesearcher.config.CubeSearcherConfig;
import dev.cubesearcher.render.StructureHighlighter;
import dev.cubesearcher.search.FoundStructure;
import dev.cubesearcher.search.StructureSearcher;

/**
 * Cube Searcher —— 纯客户端模组。
 *
 * <p>搜索的目标方块、结构尺寸（长/宽/高）以及「是否区分长宽的 x/z 方向」都来自
 * {@code config/cubesearcher.properties}（见 {@link CubeSearcherConfig}），
 * 每次按下快捷键都会重新读取该文件。</p>
 *
 * <p>按下快捷键（默认 V，可在「选项 - 控制」里改）：</p>
 * <ol>
 *     <li>若当前没有高亮：在渲染范围内搜索符合配置的长方体结构；
 *     没找到就在聊天栏提示「未搜索到指定结构」，找到就高亮它并停止搜索。</li>
 *     <li>若当前已有高亮：再次按下即解除高亮。</li>
 * </ol>
 */
public final class CubeSearcherClient implements ClientModInitializer {
	public static final String MOD_ID = "cubesearcher";

	/** 自定义按键分类（翻译键：key.category.cubesearcher.main）。 */
	private static final KeyMapping.Category KEY_CATEGORY =
			KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));

	/** 触发搜索 / 解除高亮的快捷键，默认 V。 */
	private static final KeyMapping SEARCH_KEY = KeyMappingHelper.registerKeyMapping(
			new KeyMapping("key.cubesearcher.search", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, KEY_CATEGORY));

	@Override
	public void onInitializeClient() {
		// 读取（必要时生成）配置文件
		CubeSearcherConfig.get().reload();

		StructureHighlighter.register();
		ClientTickEvents.END_CLIENT_TICK.register(CubeSearcherClient::onEndClientTick);
	}

	private static void onEndClientTick(Minecraft client) {
		// while + consumeClick 保证一次按键只处理一次
		while (SEARCH_KEY.consumeClick()) {
			toggleSearch(client);
		}
	}

	private static void toggleSearch(Minecraft client) {
		final ClientLevel level = client.level;

		// 还没进世界（标题界面 / 加载中）时什么也不做
		if (level == null || client.player == null) {
			return;
		}

		// 每次都重新读配置：改完 config/cubesearcher.properties 按一下快捷键就生效
		final CubeSearcherConfig config = CubeSearcherConfig.get();
		config.reload();

		// 配置有问题时提醒一次（同样的内容只提醒一次）
		final String warning = config.takeNewWarning();

		if (warning != null) {
			client.player.sendSystemMessage(Component.translatable("cubesearcher.message.config_warning", warning));
		}

		// 已有高亮 -> 再按一次解除
		if (StructureHighlighter.hasHighlight()) {
			StructureHighlighter.clear();
			client.player.sendSystemMessage(Component.translatable("cubesearcher.message.cleared"));
			return;
		}

		final FoundStructure found = StructureSearcher.search(level, client.player.blockPosition(), config.getOptions());

		if (found == null) {
			// 未搜索到结构：提示并停止
			client.player.sendSystemMessage(Component.translatable("cubesearcher.message.not_found"));
			return;
		}

		// 搜索到结构：高亮并停止搜索（StructureSearcher 命中即返回）
		StructureHighlighter.highlight(level, found);
		client.player.sendSystemMessage(Component.translatable("cubesearcher.message.found",
				found.sizeX(), found.sizeY(), found.sizeZ(),
				found.minX(), found.minY(), found.minZ()));
	}
}
