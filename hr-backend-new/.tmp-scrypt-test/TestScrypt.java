import org.bouncycastle.crypto.generators.SCrypt;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class TestScrypt {
    public static void main(String[] args) {
        String full = "scrypt:32768:8:1$ZDmCxXbtKLJQlUDw$" + args[0];
        String body = full.substring("scrypt:".length());
        String[] parts = body.split("\\$");
        String[] params = parts[0].split(":");
        int n = Integer.parseInt(params[0]);
        int r = Integer.parseInt(params[1]);
        int p = Integer.parseInt(params[2]);
        byte[] salt = parts[1].getBytes(StandardCharsets.UTF_8);
        byte[] expected = hexDecode(parts[2]);
        byte[] computed = SCrypt.generate("123456".getBytes(StandardCharsets.UTF_8), salt, n, r, p, 64);
        boolean match = Arrays.equals(computed, expected);
        System.out.println("n=" + n + " r=" + r + " p=" + p + " salt=" + parts[1]);
        System.out.println("MATCH: " + match);
        if (!match) {
            System.out.println("computed=" + toHex(computed).substring(0, 32));
            System.out.println("expected=" + toHex(expected).substring(0, 32));
        }
    }

    static byte[] hexDecode(String hex) {
        byte[] out = new byte[hex.length() / 2];
        for (int i = 0; i < hex.length(); i += 2)
            out[i / 2] = (byte) Integer.parseInt(hex.substring(i, i + 2), 16);
        return out;
    }

    static String toHex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }
}
