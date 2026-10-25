package app.nova;
import app.nova.core.Rules;
import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
class RulesTest {
    JsonArray r(String s) { return JsonParser.parseString(s).getAsJsonArray(); }
    @Test void emptyAllows() { assertTrue(Rules.allowed(null, Set.of(), "windows", "x86_64")); }
    @Test void osRule() {
        var rules = r("[{\"action\":\"allow\",\"os\":{\"name\":\"osx\"}}]");
        assertFalse(Rules.allowed(rules, Set.of(), "windows", "x86_64"));
        assertTrue(Rules.allowed(rules, Set.of(), "osx", "x86_64"));
    }
    @Test void lastMatchWins() {
        var rules = r("[{\"action\":\"allow\"},{\"action\":\"disallow\",\"os\":{\"name\":\"windows\"}}]");
        assertFalse(Rules.allowed(rules, Set.of(), "windows", "x86_64"));
        assertTrue(Rules.allowed(rules, Set.of(), "linux", "x86_64"));
    }
    @Test void featureRule() {
        var rules = r("[{\"action\":\"allow\",\"features\":{\"is_demo_user\":true}}]");
        assertFalse(Rules.allowed(rules, Set.of(), "windows", "x86_64"));
        assertTrue(Rules.allowed(rules, Set.of("is_demo_user"), "windows", "x86_64"));
    }
}
