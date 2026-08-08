package top.ellan.mahjong.rules.mcr;

import java.util.List;
import java.util.Objects;

/** Immutable, engine-issued scoring result. */
public final class WinEvaluation {
    private final EvaluationStatus status;
    private final Tile winningTile;
    private final WinMethod winMethod;
    private final int qualifyingFan;
    private final int flowerFan;
    private final int totalFan;
    private final List<FanAward> awards;
    private final List<InternalAward> internalAwards;
    private final List<String> violations;

    /* Package-private by design: settlement must not accept caller-forged scores. */
    WinEvaluation(EvaluationStatus status, Tile winningTile, WinMethod winMethod,
                  int qualifyingFan, int flowerFan, int totalFan,
                  List<FanAward> awards, List<InternalAward> internalAwards,
                  List<String> violations) {
        if (status == null || winningTile == null || winMethod == null) {
            throw new IllegalArgumentException("status, winning tile and method are required");
        }
        this.status = status;
        this.winningTile = winningTile;
        this.winMethod = winMethod;
        this.awards = awards == null ? List.of() : List.copyOf(awards);
        this.internalAwards = internalAwards == null ? List.of() : List.copyOf(internalAwards);
        this.violations = violations == null ? List.of() : List.copyOf(violations);
        if (qualifyingFan < 0 || flowerFan < 0
                || totalFan != Math.addExact(qualifyingFan, flowerFan)) {
            throw new IllegalArgumentException("inconsistent fan totals");
        }
        int awardQualifying = 0;
        int awardFlowers = 0;
        for (FanAward award : this.awards) {
            if (award.fan() == Fan.HUAPAI) {
                awardFlowers = Math.addExact(awardFlowers, award.points());
            } else {
                awardQualifying = Math.addExact(awardQualifying, award.points());
            }
        }
        for (InternalAward award : this.internalAwards) {
            awardQualifying = Math.addExact(awardQualifying, award.points());
        }
        if (status == EvaluationStatus.COMPLETE) {
            if (awardQualifying != qualifyingFan || awardFlowers != flowerFan) {
                throw new IllegalArgumentException("fan totals must match the typed awards");
            }
        } else if (qualifyingFan != 0 || flowerFan != 0 || totalFan != 0
                || !this.awards.isEmpty() || !this.internalAwards.isEmpty()) {
            throw new IllegalArgumentException("non-complete evaluations cannot carry points or awards");
        }
        this.qualifyingFan = qualifyingFan;
        this.flowerFan = flowerFan;
        this.totalFan = totalFan;
    }

    public EvaluationStatus status() { return status; }
    public Tile winningTile() { return winningTile; }
    public WinMethod winMethod() { return winMethod; }
    public int qualifyingFan() { return qualifyingFan; }
    public int flowerFan() { return flowerFan; }
    public int totalFan() { return totalFan; }
    public List<FanAward> awards() { return awards; }
    public List<InternalAward> internalAwards() { return internalAwards; }
    public List<String> violations() { return violations; }

    public boolean winningShape() { return status == EvaluationStatus.COMPLETE; }
    public boolean legalWin() { return winningShape() && qualifyingFan >= 8; }
    public boolean hasFan(Fan fan) { return fanCount(fan) > 0; }

    public int fanCount(Fan fan) {
        int result = 0;
        for (FanAward award : awards) if (award.fan() == fan) result += award.count();
        return result;
    }

    static WinEvaluation notWinning(Tile tile, WinMethod method) {
        return new WinEvaluation(EvaluationStatus.NOT_WINNING, tile, method, 0, 0, 0,
                List.of(), List.of(), List.of("tiles do not form an MCR winning hand"));
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof WinEvaluation that)) return false;
        return qualifyingFan == that.qualifyingFan
                && flowerFan == that.flowerFan
                && totalFan == that.totalFan
                && status == that.status
                && winningTile == that.winningTile
                && winMethod == that.winMethod
                && awards.equals(that.awards)
                && internalAwards.equals(that.internalAwards)
                && violations.equals(that.violations);
    }

    @Override
    public int hashCode() {
        return Objects.hash(status, winningTile, winMethod, qualifyingFan, flowerFan, totalFan,
                awards, internalAwards, violations);
    }

    @Override
    public String toString() {
        return "WinEvaluation[status=" + status
                + ", winningTile=" + winningTile
                + ", winMethod=" + winMethod
                + ", qualifyingFan=" + qualifyingFan
                + ", flowerFan=" + flowerFan
                + ", totalFan=" + totalFan
                + ", awards=" + awards
                + ", internalAwards=" + internalAwards
                + ", violations=" + violations + ']';
    }
}
