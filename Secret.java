// Secret.java
import java.io.*;
import java.math.BigInteger;
import java.nio.file.*;
import java.util.*;
import org.json.*; // Use org.json library: https://mvnrepository.com/artifact/org.json/json

// ---------- Fraction (exact rational) ----------
class Fraction {
    public final BigInteger num;
    public final BigInteger den;

    public Fraction(BigInteger num, BigInteger den) {
        if (den.equals(BigInteger.ZERO)) throw new ArithmeticException("Denominator zero");
        if (den.signum() < 0) {
            num = num.negate();
            den = den.negate();
        }
        BigInteger g = num.gcd(den);
        this.num = num.divide(g);
        this.den = den.divide(g);
    }

    public static Fraction zero() { return new Fraction(BigInteger.ZERO, BigInteger.ONE); }
    public static Fraction fromBigInteger(BigInteger b) { return new Fraction(b, BigInteger.ONE); }

    public Fraction add(Fraction other) {
        BigInteger n = this.num.multiply(other.den).add(other.num.multiply(this.den));
        BigInteger d = this.den.multiply(other.den);
        return new Fraction(n, d);
    }

    public Fraction sub(Fraction other) {
        BigInteger n = this.num.multiply(other.den).subtract(other.num.multiply(this.den));
        BigInteger d = this.den.multiply(other.den);
        return new Fraction(n, d);
    }

    public Fraction mul(Fraction other) {
        return new Fraction(this.num.multiply(other.num), this.den.multiply(other.den));
    }

    public Fraction div(Fraction other) {
        if (other.num.equals(BigInteger.ZERO)) throw new ArithmeticException("Divide by zero");
        return new Fraction(this.num.multiply(other.den), this.den.multiply(other.num));
    }

    public Fraction neg() { return new Fraction(this.num.negate(), this.den); }

    public String toString() {
        if (den.equals(BigInteger.ONE)) return num.toString();
        return num + "/" + den;
    }

    public BigInteger toIntegerIfExact() {
        if (den.equals(BigInteger.ONE)) return num;
        if (num.mod(den).equals(BigInteger.ZERO)) return num.divide(den);
        return null;
    }
}

// ---------- Helpers ----------
class Utils {
    public static BigInteger parseBigIntFromBase(String str, int base) {
        str = str.replace("_", "");
        BigInteger result = BigInteger.ZERO;
        BigInteger b = BigInteger.valueOf(base);
        for (char ch : str.toCharArray()) {
            int d = charToDigit(ch);
            if (d >= base) throw new IllegalArgumentException("Digit " + ch + " >= base " + base);
            result = result.multiply(b).add(BigInteger.valueOf(d));
        }
        return result;
    }

    public static int charToDigit(char ch) {
        if ('0' <= ch && ch <= '9') return ch - '0';
        if ('a' <= Character.toLowerCase(ch) && Character.toLowerCase(ch) <= 'z')
            return Character.toLowerCase(ch) - 'a' + 10;
        throw new IllegalArgumentException("Invalid digit char: " + ch);
    }

    public static Fraction lagrangeAtZero(List<Map<String, BigInteger>> pointsSubset) {
        Fraction sum = Fraction.zero();
        for (int i = 0; i < pointsSubset.size(); i++) {
            BigInteger xi = pointsSubset.get(i).get("x");
            Fraction yi = Fraction.fromBigInteger(pointsSubset.get(i).get("y"));
            Fraction li = Fraction.fromBigInteger(BigInteger.ONE);
            for (int j = 0; j < pointsSubset.size(); j++) {
                if (i == j) continue;
                BigInteger xj = pointsSubset.get(j).get("x");
                Fraction numer = new Fraction(xj.negate(), BigInteger.ONE);
                Fraction denom = new Fraction(xi.subtract(xj), BigInteger.ONE);
                li = li.mul(numer.div(denom));
            }
            sum = sum.add(yi.mul(li));
        }
        return sum;
    }

    public static List<List<Integer>> combinations(int n, int k) {
        List<List<Integer>> out = new ArrayList<>();
        backtrack(0, n, k, new ArrayList<>(), out);
        return out;
    }

    private static void backtrack(int start, int n, int k, List<Integer> chosen, List<List<Integer>> out) {
        if (chosen.size() == k) {
            out.add(new ArrayList<>(chosen));
            return;
        }
        for (int i = start; i <= n - (k - chosen.size()); i++) {
            chosen.add(i);
            backtrack(i + 1, n, k, chosen, out);
            chosen.remove(chosen.size() - 1);
        }
    }
}

// ---------- Main ----------
public class Secret {

    public static Map<String, Object> findSecretFromJSON(JSONObject testcase) {
        JSONObject keysObj = testcase.getJSONObject("keys");
        int n = keysObj.getInt("n");
        int k = keysObj.getInt("k");

        List<Map<String, BigInteger>> points = new ArrayList<>();
        for (String key : testcase.keySet()) {
            if (key.equals("keys") || !key.matches("\\d+")) continue;
            int xNum = Integer.parseInt(key);
            JSONObject entry = testcase.getJSONObject(key);
            String baseStr = entry.getString("base");
            String valueStr = entry.getString("value");
            BigInteger yBig = Utils.parseBigIntFromBase(valueStr, Integer.parseInt(baseStr));
            Map<String, BigInteger> map = new HashMap<>();
            map.put("x", BigInteger.valueOf(xNum));
            map.put("y", yBig);
            points.add(map);
        }

        points.sort(Comparator.comparing(p -> p.get("x")));

        if (points.size() < k) throw new RuntimeException("Not enough shares in input");

        List<Integer> idx = new ArrayList<>();
        for (int i = 0; i < points.size(); i++) idx.add(i);
        List<List<Integer>> comb = Utils.combinations(points.size(), k);

        Map<String, Integer> tally = new HashMap<>();
        Map<String, List<Map<String, BigInteger>>> subsetThatGave = new HashMap<>();

        for (List<Integer> c : comb) {
            List<Map<String, BigInteger>> subsetPoints = new ArrayList<>();
            for (int i : c) subsetPoints.add(points.get(i));
            Fraction secretFrac = Utils.lagrangeAtZero(subsetPoints);
            String keyStr = secretFrac.num + "/" + secretFrac.den;
            tally.put(keyStr, tally.getOrDefault(keyStr, 0) + 1);
            subsetThatGave.putIfAbsent(keyStr, subsetPoints);
        }

        String bestKey = null;
        int bestCount = -1;
        for (Map.Entry<String, Integer> e : tally.entrySet()) {
            if (e.getValue() > bestCount) {
                bestCount = e.getValue();
                bestKey = e.getKey();
            }
        }

        if (bestKey == null) throw new RuntimeException("Could not determine modal secret");

        String[] parts = bestKey.split("/");
        Fraction secretFrac = new Fraction(new BigInteger(parts[0]), new BigInteger(parts[1]));
        BigInteger intValue = secretFrac.toIntegerIfExact();

        Map<String, Object> res = new HashMap<>();
        if (intValue != null) {
            res.put("secret", intValue);
        } else {
            res.put("secretFraction", secretFrac);
        }
        res.put("subset", subsetThatGave.get(bestKey));
        return res;
    }

    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            System.err.println("Usage: java Secret <testcase.json>");
            System.exit(1);
        }
        String raw = new String(Files.readAllBytes(Paths.get(args[0])));
        JSONObject testcase = new JSONObject(raw);

        try {
            Map<String, Object> res = findSecretFromJSON(testcase);
            if (res.containsKey("secret")) {
                System.out.println("Secret: " + res.get("secret"));
            } else {
                System.out.println("Secret (rational): " + res.get("secretFraction"));
            }
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
        }
    }
}
