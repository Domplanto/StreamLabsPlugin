package me.Domplanto.streamLabs.condition.operator;

import me.Domplanto.streamLabs.condition.Operator;

@SuppressWarnings("unused")
public class EqualityOperator implements Operator {
    @Override
    public String getName() {
        return "=";
    }

    @Override
    public boolean check(Object element1, Object element2) {
        return element1.equals(element2);
    }
}
