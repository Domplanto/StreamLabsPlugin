package me.Domplanto.streamLabs.config.condition;

import com.google.gson.JsonObject;
import me.Domplanto.streamLabs.config.ActionPlaceholder;
import me.Domplanto.streamLabs.events.StreamlabsEvent;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

public class Condition {
    private static final Set<? extends Operator> OPERATORS = Operator.findOperatorClasses();
    private final Function<JsonObject, String> element1;
    private final Function<JsonObject, String> element2;
    private final Operator operator;

    private Condition(Operator operator, Function<JsonObject, String> element1, Function<JsonObject, String> element2) {
        this.element1 = element1;
        this.element2 = element2;
        this.operator = operator;
    }

    public boolean check(JsonObject object) {
        String e1 = element1.apply(object);
        String e2 = element2.apply(object);
        try {
            return this.operator.check(Double.parseDouble(e1), Double.parseDouble(e2));
        } catch (NumberFormatException e) {
            return this.operator.check(e1, e2);
        }
    }

    public static List<Condition> parseAll(List<String> conditionStrings, StreamlabsEvent event) {
        return conditionStrings.stream()
                .map(string -> {
                    Operator op = OPERATORS.stream()
                            .filter(operator1 -> string.contains(operator1.getName()))
                            .findFirst().orElse(null);
                    if (op == null) return null;

                    String[] elements = string.split(op.getName());
                    return new Condition(op, parseElement(elements[0], event), parseElement(elements[1], event));
                }).toList();
    }

    private static Function<JsonObject, String> parseElement(String elementString, StreamlabsEvent event) {
        Function<JsonObject, String> defaultFunc = o -> elementString;
        if (!elementString.startsWith("{") || !elementString.endsWith("}") || elementString.length() < 3)
            return defaultFunc;

        String placeholderName = elementString.substring(1, elementString.length() - 1);
        return event.getPlaceholders()
                .stream()
                .filter(placeholder -> placeholder.name().equals(placeholderName))
                .min(Comparator.comparingInt(p -> p.name().length()))
                .map(ActionPlaceholder::valueFunction)
                .orElse(defaultFunc);
    }
}
