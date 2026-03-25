package jdd.nightMare.Game;

public enum GamePhase {
    DAY_1("Day1"), NIGHT_1("Night1"),
    DAY_2("Day2"), NIGHT_2("Night2"),
    DAY_3("Day3(自毁)"), NIGHT_3("Night3"),
    DAY_4("Day4"), NIGHT_4("Night4"),
    DAY_5("Day5(收缩)"), NIGHT_5("Night5"),
    DAY_6("Day6(终局)");
    public boolean isNight() {
        return this.name().startsWith("NIGHT");
    }
    private final String displayName;

    GamePhase(String displayName) { this.displayName = displayName; }
    public String getDisplayName() { return displayName; }

    public GamePhase next() {
        int nextOrd = this.ordinal() + 1;
        if (nextOrd < values().length) return values()[nextOrd];
        return this;
    }
}