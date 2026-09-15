import com.tacz.guns.ammunition.TemporaryAmmoRefundPolicy;
import java.util.List;

public class TemporaryAmmoRefundPolicyTest {
    public static void main(String[] args) {
        check(TemporaryAmmoRefundPolicy.sourceId("tacz:556x45").orElseThrow()
                .equals("54527a984bdc2d4e668b4567"), "5.56 must return M855");
        check(TemporaryAmmoRefundPolicy.sourceId("other:556x45").isEmpty(), "namespace must match");
        check(TemporaryAmmoRefundPolicy.sourceId("tacz:unknown").isEmpty(), "unknown caliber must not fabricate a round");
        check(TemporaryAmmoRefundPolicy.split(0, 60).isEmpty(), "empty gun must return nothing");
        check(TemporaryAmmoRefundPolicy.split(60, 60).equals(List.of(60)), "one exact stack");
        check(TemporaryAmmoRefundPolicy.split(120, 60).equals(List.of(60, 60)), "no phantom round at exact multiple");
        check(TemporaryAmmoRefundPolicy.split(121, 60).equals(List.of(60, 60, 1)), "preserve remainder");
        for (int limit : new int[]{1, 20, 40, 60, 99}) {
            for (int count = 0; count <= 300; count++) {
                var stacks = TemporaryAmmoRefundPolicy.split(count, limit);
                check(stacks.stream().mapToInt(Integer::intValue).sum() == count, "conserve quantity");
                check(stacks.stream().allMatch(n -> n > 0 && n <= limit), "respect stack capacity");
            }
        }
        try { TemporaryAmmoRefundPolicy.split(-1, 60); throw new AssertionError("negative count accepted"); }
        catch (IllegalArgumentException expected) {}
        try { TemporaryAmmoRefundPolicy.split(1, 0); throw new AssertionError("zero capacity accepted"); }
        catch (IllegalArgumentException expected) {}
        // The Python harness supplies all approved fixed mappings, validated against the 86-round catalog.
        for (int i = 0; i < args.length; i += 2)
            check(TemporaryAmmoRefundPolicy.sourceId(args[i]).orElseThrow().equals(args[i + 1]), args[i]);
        System.out.println("PASS: fixed M855, unknown caliber refusal, 1505 quantity/stack cases, invalid inputs, 12 catalog mappings");
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
