package me.Domplanto.streamLabs.config.condition.operator;

import me.Domplanto.streamLabs.config.condition.Operator;

@SuppressWarnings("unused")
public class ContainsOperator implements Operator {
    @Override
    public String getName() {
        return ".>";
    }

    @Override
    public boolean check(Object element1, Object element2) {
        if (!(element1 instanceof String s1) || !(element2 instanceof String s2)) return false;

        return s1.contains(s2);
    }
}
