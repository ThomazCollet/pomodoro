package com.thomazcollet.domain.model;

public enum RankingType {
    E(0),
    D(500),
    C(2000),
    B(6000),
    A(15000),
    S(40000),
    SS(100000);

    private final int minXp;

    RankingType(int minXp) {
        this.minXp = minXp;
    }

    public int getMinXp() {
        return minXp;
    }

    /**
     * Varre os rankings do maior para o menor (Fail-Fast)
     * retornando o maior tier que o XP do usuário alcança.
     */
    public static RankingType fromXp(int xp) {
        if (xp >= SS.minXp)
            return SS;
        if (xp >= S.minXp)
            return S;
        if (xp >= A.minXp)
            return A;
        if (xp >= B.minXp)
            return B;
        if (xp >= C.minXp)
            return C;
        if (xp >= D.minXp)
            return D;
        return E;
    }
}