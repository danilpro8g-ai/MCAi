package com.apocscode.mcai.ai;

/** Keeps machine tool instructions out of player-facing messages. */
public final class PlayerReplies {
    private PlayerReplies() {}
    public static String resource(String id) {
        return switch (id) {
            case "coal" -> "уголь"; case "iron" -> "железо"; case "copper" -> "медь";
            case "gold" -> "золото"; case "diamond" -> "алмазы"; case "emerald" -> "изумруды";
            case "lapis" -> "лазурит"; case "redstone" -> "редстоун"; case "quartz" -> "кварц";
            default -> id;
        };
    }
    public static String toolResult(String result) {
        if (result == null || result.isBlank()) return "Команда не вернула результат. Проверь статус задания: !статус.";
        if (result.contains("[ASYNC_TASK]")) return "Задание принято в очередь. Начинаю работу; результат сообщу после выполнения.";
        if (result.startsWith("No companion")) return "Спутник не найден. Сначала призови его.";
        if (result.startsWith("Already have")) return "Нужные ресурсы уже есть в инвентаре или хранилище.";
        if (result.startsWith("Unknown block")) return "Такой блок не найден. Уточни название ресурса.";
        if (result.matches("(?s).*[а-яА-Я].*") && !result.contains("STOP calling")) return result;
        return "Операция не подтверждена. Проверь инструмент, доступность ресурса и !статус. Подробности записаны в журнал MCAi.";
    }
}
