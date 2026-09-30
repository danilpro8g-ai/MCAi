package com.apocscode.mcai.ai;

import com.apocscode.mcai.ai.tool.*;
import com.apocscode.mcai.config.AiConfig;
import com.apocscode.mcai.entity.CompanionEntity;
import com.apocscode.mcai.network.ChatMessageHandler;
import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerPlayer;
import java.util.*;
import java.util.function.Consumer;

/** Server-thread coordinator. The model can propose, but cannot invoke tools. */
public final class IntentController {
    private IntentController() {}
    private static final Map<UUID, State> STATES = new HashMap<>();
    private static class State {
        long version, updated;
        IntentRules.Intent pending, previous;
        String clarification;
    }
    public static void invalidate(ServerPlayer player) { STATES.remove(player.getUUID()); }
    public static void handle(String text, ServerPlayer player, CompanionEntity companion, Consumer<String> reply) {
        if (companion == null || !companion.isAlive() || !player.getUUID().equals(companion.getOwnerUUID())) {
            reply.accept("Твой спутник не найден. Сначала призови его."); return;
        }
        long now = System.currentTimeMillis();
        STATES.entrySet().removeIf(e -> now - e.getValue().updated > 600_000);
        State state = STATES.computeIfAbsent(player.getUUID(), id -> new State());
        boolean expired = now - state.updated > 120_000;
        if (expired) state.clarification = null;
        state.updated = now;
        long version = ++state.version;
        String normalized = RussianCommands.normalize(text);
        String quick = RussianCommands.quick(normalized);
        if (Set.of("да", "выполняй", "подтверждаю").contains(normalized)) {
            IntentRules.Intent intent = state.pending; state.pending = null;
            if (intent == null || expired) { reply.accept("Нет актуального предложения. Напиши команду заново."); return; }
            if (companion.getTaskManager().hasTasks()) { reply.accept("Уже выполняю задание. Сначала отмени его или дождись результата."); return; }
            try {
                if (intent.action().equals("gather")) {
                    RussianCommands.Gather gather = new RussianCommands.Gather(intent.resource(), intent.count(), null);
                    var tool = ToolRegistry.get(gather.tool());
                    if (tool == null || !AiConfig.isToolEnabled(gather.tool())) { reply.accept("Этот инструмент отключён в настройках."); return; }
                    JsonObject args = new JsonObject();
                    args.addProperty(gather.resourceKey(), gather.block()); args.addProperty(gather.countKey(), gather.count());
                    args.addProperty("additional", true);
                    args.addProperty("confirmedIntent", true);
                    String result = tool.execute(args, new ToolContext(player, player.getServer()));
                    if (result != null && result.contains("[ASYNC_TASK]")) state.previous = intent;
                    reply.accept(PlayerReplies.toolResult(result));
                } else {
                    state.previous = intent;
                    reply.accept(ChatMessageHandler.handleQuickCommand(intent.action(), companion, player));
                }
            } catch (Exception error) {
                com.apocscode.mcai.MCAi.LOGGER.error("Confirmed intent failed", error);
                reply.accept("Не удалось запустить задание. Проверь !статус и журнал MCAi.");
            }
            return;
        }
        state.pending = null;
        if (Set.of("нет", "не надо").contains(normalized)) { reply.accept("Предложение отменено."); return; }
        if (quick != null && Set.of("cancel", "stay", "status").contains(quick)) {
            reply.accept(ChatMessageHandler.handleQuickCommand(quick, companion, player)); return;
        }
        StringBuilder inventory = new StringBuilder();
        var items = companion.getCompanionInventory();
        for (int i = 0; i < items.getContainerSize(); i++) {
            var stack = items.getItem(i);
            if (!stack.isEmpty()) inventory.append(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()))
                .append(':').append(stack.getCount()).append(' ');
        }
        String snapshot = "Здоровье=" + companion.getHealth() + "; позиция=" + companion.blockPosition()
            + "; задания=" + companion.getTaskManager().getStatusSummary() + "; инвентарь=" + inventory
            + "; окружающие ресурсы не обследованы";
        String previous = state.previous == null ? "нет" : state.previous.toString();
        reply.accept("Разбираю команду…");
        String input = state.clarification != null && normalized.matches("[0-9]+")
            ? "Предыдущий запрос: " + state.clarification + ". Уточнение количества: " + text : text;
        state.clarification = null;
        IntentService.understand(input, snapshot, previous).whenComplete((intent, error) -> player.getServer().execute(() -> {
            if (STATES.get(player.getUUID()) != state || state.version != version || !companion.isAlive()
                || player.getServer().getPlayerList().getPlayer(player.getUUID()) != player) return;
            if (error != null) {
                com.apocscode.mcai.MCAi.LOGGER.warn("Intent understanding failed", error);
                reply.accept("Не удалось разобрать ответ Ollama. Проверь подключение и повтори команду с ресурсом и количеством."); return;
            }
            if (intent.needsConfirmation()) {
                state.pending = intent; state.updated = System.currentTimeMillis();
                reply.accept("Я понял: " + intent.description() + ". Напиши «да», чтобы начать, или «нет», чтобы отменить.");
            } else if (Set.of("cancel", "stay", "status").contains(intent.action())) {
                reply.accept(ChatMessageHandler.handleQuickCommand(intent.action(), companion, player));
            } else {
                if (intent.action().equals("clarify")) state.clarification = input;
                reply.accept(intent.reply());
            }
        }));
    }
}
