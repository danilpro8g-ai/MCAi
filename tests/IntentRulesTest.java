import com.apocscode.mcai.ai.IntentRules.Intent;
public class IntentRulesTest {
    static void reject(String action, String resource, int count) {
        try { new Intent(action, resource, count, ""); }
        catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Accepted invalid intent: " + action + resource + count);
    }
    public static void main(String[] args) {
        Intent dirt = new Intent("gather", "minecraft:dirt", 4, "");
        if (!dirt.needsConfirmation() || !dirt.resource().equals("dirt") || !dirt.description().contains("земля")) throw new AssertionError();
        if (!new Intent("follow", "", 0, "").needsConfirmation()) throw new AssertionError();
        if (new Intent("cancel", "", 0, "").needsConfirmation()) throw new AssertionError();
        reject("run_command", "", 0); reject("gather", "tnt", 4);
        reject("gather", "dirt", 0); reject("gather", "dirt", 129);
        reject("status", "dirt", 4); reject("gather", "mod:iron", 4);
        System.out.println("Intent validation checks passed");
    }
}
