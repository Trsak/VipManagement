package cz.trsak.VipManagement;

import java.util.Map;
import java.util.HashMap;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class TimeReader{

    private final Map<String, Long> units = new HashMap<String, Long>();

    private static final String UNIT_PATTERN = "\\w+";
    private static final Pattern ITEM_PATTERN = Pattern.compile("(\\d+)\\s*("
            + UNIT_PATTERN + ")");

    /**
     * Add a new time unit.
     *
     * @param unit
     *            the unit, e.g. "s"
     * @param value
     *            the unit's modifier value (multiplier from milliseconds, e.g.
     *            1000)
     * @return self reference for chaining
     */
    public TimeReader addUnit(final String unit, final long value){
        if(value < 0 || !unit.matches(UNIT_PATTERN)){
            throw new IllegalArgumentException();
        }
        units.put(unit, Long.valueOf(value));
        return this;
    }

    /**
     * Parse a string using the defined units.
     *
     * @return the resulting number of milliseconds
     */
    public long parse(final String input){
        long value = 0l;
        final Matcher matcher = ITEM_PATTERN.matcher(input);
        while(matcher.find()){
            final long modifier = Long.parseLong(matcher.group(1));
            final String unit = matcher.group(2);
            if(!units.containsKey(unit)){
                throw new IllegalArgumentException("Unrecognized token: "
                        + unit);
            }
            value += units.get(unit).longValue() * modifier;
        }
        return value;
    }

}
