package vn.soulpets.pet;
public record Stats(double hp, double atk, double def, double speed) {
    public static final Stats ZERO = new Stats(0, 0, 0, 0);
    public Stats plus(Stats o) { return new Stats(hp + o.hp, atk + o.atk, def + o.def, speed + o.speed); }
    public Stats scale(double k) { return new Stats(hp * k, atk * k, def * k, speed * k); }
}
