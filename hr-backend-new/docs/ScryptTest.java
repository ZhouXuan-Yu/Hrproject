import org.bouncycastle.crypto.generators.SCrypt;
import java.util.Base64;

public class ScryptTest {
    public static void main(String[] args) {
        String stored = "scrypt:32768:8:1$ZDmCxXbtKLJQlUDw$a5e058b5dd1cfaa85a577907049b5cbf542b89fd9aee65b7065ebb79fd305ca59124a21ad3022fda97f317325e499723043268c13760fa0a01d980f5d9b26b3d";
        String body = stored.substring("scrypt:".length());
        String[] parts = body.split("\\$");
        System.out.println("parts len=" + parts.length);
        String[] params = parts[0].split(":");
        System.out.println("params len=" + params.length);
        int n = Integer.parseInt(params[0]), r = Integer.parseInt(params[1]), p = Integer.parseInt(params[2]);
        int dkLen = params.length == 4 ? Integer.parseInt(params[3]) : 64;
        byte[] salt = Base64.getDecoder().decode(parts[1]);
        byte[] expected = Base64.getDecoder().decode(parts[2]);
        System.out.println("salt bytes=" + salt.length + " expected bytes=" + expected.length);
        byte[] computed = SCrypt.generate("123456".getBytes(), salt, n, r, p, dkLen);
        System.out.println("computed b64=" + Base64.getEncoder().encodeToString(computed));
        System.out.println("expected  b64=" + Base64.getEncoder().encodeToString(expected));
        System.out.println("match=" + java.util.Arrays.equals(computed, expected));
    }
}
