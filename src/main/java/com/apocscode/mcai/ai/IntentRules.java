package com.apocscode.mcai.ai;

import java.util.Map;
import java.util.Set;

/** Whitelist shared by model output validation and confirmation UI. */
public final class IntentRules {
    private IntentRules() {}
    public static final Map<String, String> NAMES = Map.ofEntries(
        Map.entry("dirt", "земля"), Map.entry("sand", "песок"), Map.entry("gravel", "гравий"),
        Map.entry("clay", "глина"), Map.entry("cobblestone", "булыжник"), Map.entry("stone", "камень"),
        Map.entry("coal", "уголь"), Map.entry("iron", "железо"), Map.entry("copper", "медь"),
        Map.entry("gold", "золото"), Map.entry("diamond", "алмазы"), Map.entry("emerald", "изумруды"),
        Map.entry("lapis", "лазурит"), Map.entry("redstone", "редстоун"), Map.entry("quartz", "кварц"),
        Map.entry("wood", "древесина"), Map.entry("oak_log", "дубовые брёвна"),
        Map.entry("birch_log", "берёзовые брёвна"), Map.entry("spruce_log", "еловые брёвна"));
    private static final Set<String> ACTIONS = Set.of("gather", "follow", "come", "stay", "cancel", "status", "clarify", "chat");
    public record Intent(String action, String resource, int count, String reply) {
        public Intent {
            if (!ACTIONS.contains(action)) throw new IllegalArgumentException("Unknown action");
            if (action.equals("gather")) {
                resource = resource.startsWith("minecraft:") ? resource.substring(10) : resource;
                if (!NAMES.containsKey(resource) || count < 1 || count > 128)
                    throw new IllegalArgumentException("Invalid resource or count");
            } else if (!resource.isEmpty() || count != 0) {
                throw new IllegalArgumentException("Unexpected resource or count");
            }
            if (reply == null || reply.length() > 500) throw new IllegalArgumentException("Invalid reply");
        }
        public boolean needsConfirmation() { return Set.of("gather", "follow", "come").contains(action); }
        public String description() {
            return switch (action) {
                case "gather" -> "Добыть " + count + " новых блоков: " + NAMES.get(resource);
                case "follow" -> "Следовать за тобой";
                case "come" -> "Подойти к тебе";
                default -> action;
            };
        }
    }
}
