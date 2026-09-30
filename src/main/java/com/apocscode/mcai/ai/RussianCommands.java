package com.apocscode.mcai.ai;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** Exact Russian commands; resource names are never guessed by fuzzy matching. */
public final class RussianCommands {
    private RussianCommands() {}
    private static final Map<String, String> QUICK = Map.ofEntries(
        Map.entry("стой", "stay"), Map.entry("жди", "stay"),
        Map.entry("за мной", "follow"), Map.entry("следуй за мной", "follow"),
        Map.entry("ко мне", "come"), Map.entry("иди сюда", "come"),
        Map.entry("отмена", "cancel"), Map.entry("отмени", "cancel"), Map.entry("стоп", "cancel"),
        Map.entry("статус", "status"), Map.entry("здоровье", "health"),
        Map.entry("авто", "auto"), Map.entry("экипировка", "equip"), Map.entry("помощь", "help"));
    private static final Map<String, String> BLOCKS = Map.ofEntries(
        Map.entry("земля", "dirt"), Map.entry("земли", "dirt"), Map.entry("землю", "dirt"),
        Map.entry("песок", "sand"), Map.entry("песка", "sand"),
        Map.entry("булыжник", "cobblestone"), Map.entry("булыжника", "cobblestone"),
        Map.entry("гравий", "gravel"), Map.entry("гравия", "gravel"),
        Map.entry("глина", "clay"), Map.entry("глину", "clay"), Map.entry("глины", "clay"));

    private static final Map<String, String> RESOURCES = new java.util.HashMap<>(BLOCKS);
    static {
        aliases("coal", "уголь угля углю"); aliases("iron", "железо железа");
        aliases("copper", "медь меди"); aliases("gold", "золото золота");
        aliases("diamond", "алмаз алмазы алмазов алмаза"); aliases("emerald", "изумруд изумруды изумрудов изумруда");
        aliases("redstone", "редстоун редстоуна красная_пыль красной_пыли");
        aliases("lapis", "лазурит лазурита"); aliases("quartz", "кварц кварца");
        aliases("wood", "дерево дерева древесина древесины древесину бревна бревен дрова дров");
        aliases("oak_log", "дуб дуба дубовые_бревна дубовых_бревен дубовых_бревна");
        aliases("birch_log", "береза березы березовые_бревна березовых_бревен");
        aliases("spruce_log", "ель ели еловые_бревна еловых_бревен");
        aliases("stone", "камень камня камни");
    }
    private static void aliases(String id, String names) {
        for (String name : names.split(" ")) RESOURCES.put(name.replace('_', ' '), id);
    }
    private static final java.util.Set<String> ORES = java.util.Set.of("coal", "iron", "copper", "gold", "diamond", "emerald", "redstone", "lapis", "quartz");
    private static final Pattern GATHER = Pattern.compile("^(?:на\\s+копай|накопай|копай|добудь|добывай|собери|выкопай|наруби|сруби|руби|принеси)\\s+(.+)$");
    public static String normalize(String text) {
        return text.toLowerCase(Locale.ROOT).replace('ё', 'е').trim()
            .replaceAll("\\s+", " ").replaceAll("[.!]+$", "").trim();
    }
    public static String quick(String text) { return QUICK.get(normalize(text)); }
    public record Gather(String block, int count, String error) {
        public String tool() { return ORES.contains(block) ? "mine_ores" : "wood".equals(block) ? "chop_trees" : "gather_blocks"; }
        public String resourceKey() { return ORES.contains(block) ? "ore" : "block"; }
        public String countKey() { return ORES.contains(block) ? "maxOres" : "wood".equals(block) ? "maxLogs" : "maxBlocks"; }
    }
    /** Context is owned by one player, expires after ten minutes, and never guesses a resource. */
    public static final class Session {
        private String resource;
        private long updated;
        private int lastCount;
        public Gather parse(String text, long now) {
            if (now - updated > 600_000 || now < updated) resource = null;
            String msg = normalize(text).replaceAll("^пожалуйста[, ]+|[, ]+пожалуйста$", "");
            var follow = Pattern.compile("^(?:еще|ещe|столько же|повтори)(?:\\s+(\\d+))?$").matcher(msg);
            if (follow.matches()) {
                if (resource == null) return error("Что добыть? Например: добудь 4 угля.");
                if (follow.group(1) == null && lastCount == 0) return error("Укажи количество: еще 4.");
                msg = "добудь " + (follow.group(1) == null ? lastCount : follow.group(1)) + " " + resource;
            } else if (msg.matches("\\d+")) {
                if (resource == null) return error("Укажи ресурс: например, добудь " + msg + " земли.");
                msg = "добудь " + msg + " " + resource;
            }
            var match = GATHER.matcher(msg);
            if (!match.matches()) return null;
            String rest = match.group(1).replaceAll("^(?:мне|пожалуйста)\\s+", "")
                .replaceAll("(?:^|\\s)(?:блок|блока|блоков|штук|штуки|шт)(?=\\s|$)", " ")
                .replaceAll("\\s+", " ").trim();
            var count = Pattern.compile("^(?:(\\d+)\\s+(.+)|(.+?)\\s+(\\d+))$").matcher(rest);
            if (!count.matches()) {
                if (RESOURCES.containsKey(rest)) { resource = rest; lastCount = 0; updated = now; }
                else resource = null;
                return error("Сколько добыть? Напиши ресурс и число, например: накопай 4 блока земли.");
            }
            String name = (count.group(2) != null ? count.group(2) : count.group(3)).trim();
            String number = count.group(1) != null ? count.group(1) : count.group(4);
            int amount;
            try { amount = Integer.parseInt(number); } catch (NumberFormatException e) { amount = 0; }
            if (amount < 1 || amount > 128) return error("Укажи количество от 1 до 128 блоков.");
            String block = RESOURCES.get(name);
            if (block == null) { resource = null; return error("Не распознан ресурс «" + name + "». Укажи один ресурс: земля, песок, булыжник, уголь, железо, медь, золото, алмазы или древесина."); }
            resource = name; lastCount = amount; updated = now;
            return new Gather(block, amount, null);
        }
        public void clear() { resource = null; }
    }
    private static Gather error(String text) { return new Gather(null, 0, text); }
    public static Gather gather(String text) { return new Session().parse(text, 0); }
}
