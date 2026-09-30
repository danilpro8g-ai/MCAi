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
        System.out.println("Russian command regression checks passed");
    }
    private static void gather(String text, String block, int count) {
        var result = RussianCommands.gather(text);
        if (result == null || result.error() != null || !block.equals(result.block()) || count != result.count())
            throw new AssertionError(text + ": " + result);
    }
}
