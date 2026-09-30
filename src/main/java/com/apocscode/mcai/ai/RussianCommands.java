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
    private static final Pattern GATHER = Pattern.compile("^(?:накопай|добудь|собери|выкопай)\\s+(.+)$");
    public static String normalize(String text) {
        return text.toLowerCase(Locale.ROOT).replace('ё', 'е').trim()
            .replaceAll("\\s+", " ").replaceAll("[.!]+$", "").trim();
    }
    public static String quick(String text) { return QUICK.get(normalize(text)); }
    public record Gather(String block, int count, String error) {}
    public static Gather gather(String text) {
        var match = GATHER.matcher(normalize(text));
        if (!match.matches()) return null;
        String rest = match.group(1).replaceAll("(?:^|\\s)(?:блок|блока|блоков|штук|штуки|шт)(?=\\s|$)", " ").trim();
        var count = Pattern.compile("^(?:(\\d+)\\s+(.+)|(.+?)\\s+(\\d+))$").matcher(rest);
        if (!count.matches()) return new Gather(null, 0, "Укажи ресурс и количество: накопай 4 блока земли.");
        String name = (count.group(2) != null ? count.group(2) : count.group(3)).trim();
        String number = count.group(1) != null ? count.group(1) : count.group(4);
        int amount;
        try { amount = Integer.parseInt(number); }
        catch (NumberFormatException e) { amount = 0; }
        if (amount < 1 || amount > 128) return new Gather(null, 0, "Укажи количество от 1 до 128 блоков.");
        String block = BLOCKS.get(name);
        if (block == null) return new Gather(null, 0, "Не распознан ресурс «" + name + "». Пока поддерживаются земля, песок, булыжник, гравий и глина.");
        return new Gather(block, amount, null);
    }
}
