import com.apocscode.mcai.ai.RussianCommands;

public class RussianCommandsTest {
    public static void main(String[] args) {
        gather("накопай земли 4 блока", "dirt", 4);
        gather("НАКОПАЙ 4 БЛОКА ЗЕМЛИ!", "dirt", 4);
        gather("собери 2 песка", "sand", 2);
        gather("добудь гравия 128", "gravel", 128);
        for (String text : new String[]{"накопай 0 земли", "накопай 129 земли", "накопай 999999999999 земли",
                "накопай земли", "накопай 4 неизвестного", "накопай 4 земли и 2 песка", "накопай -4 земли"}) {
            if (RussianCommands.gather(text).error() == null) throw new AssertionError(text);
        }
        if (!"come".equals(RussianCommands.quick(" КО   МНЕ! "))) throw new AssertionError("come");
        if (!"cancel".equals(RussianCommands.quick("отмена"))) throw new AssertionError("cancel");
        if (RussianCommands.quick("не стой") != null) throw new AssertionError("negation");
        if (RussianCommands.gather("привет") != null) throw new AssertionError("chat");
        gather("на копай земли 4 блока", "dirt", 4);
        gather("добудь 2 угля", "coal", 2);
        gather("наруби 8 древесины", "wood", 8);
        gather("собери 3 дубовых бревна", "oak_log", 3);
        var session = new RussianCommands.Session();
        if (session.parse("добудь уголь", 1).error() == null) throw new AssertionError("clarify");
        if (!"coal".equals(session.parse("4", 2).block())) throw new AssertionError("context");
        if (session.parse("еще 2", 3).count() != 2) throw new AssertionError("more");
        if (session.parse("столько же", 4).count() != 2) throw new AssertionError("repeat");
        if (session.parse("еще 4", 700000).error() == null) throw new AssertionError("expiry");
        if (new RussianCommands.Session().parse("еще 4", 1).error() == null) throw new AssertionError("isolation");
        if (!RussianCommands.gather("добудь 4 железа").countKey().equals("maxOres")) throw new AssertionError("ore routing");
        if (!RussianCommands.gather("наруби 4 дерева").countKey().equals("maxLogs")) throw new AssertionError("wood routing");
        System.out.println("Russian command regression checks passed");
    }
    private static void gather(String text, String block, int count) {
        var result = RussianCommands.gather(text);
        if (result == null || result.error() != null || !block.equals(result.block()) || count != result.count())
            throw new AssertionError(text + ": " + result);
    }
}
