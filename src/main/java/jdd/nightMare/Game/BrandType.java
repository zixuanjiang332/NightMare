package jdd.nightMare.Game;
public enum BrandType {
    ADAPTIVE_5("ADAPTIVE_5", "§c自适应五级", "§7血量高于80%时, 攻击有10%概率造成120%伤害", 40),
    CORRUPTION_5("CORRUPTION_5", "§5腐败三级", "§7死亡时给予周围敌人凋零2(3秒)", 200),
    TNT_SUPPLY("TNT_SUPPLY", "§4TNT", "§7开局90秒后获得 1 个 TNT", 0),
    DEFENDER("DEFENDER", "§e保卫者", "§7开局90秒后获得 8 个木板", 0),
    SHACKLES("SHACKLES", "§8枷锁制裁", "§7攻击有15%概率造成挖掘疲劳 I (3秒)", 60),
    ARIES("ARIES", "§f白羊", "§7放置羊毛时有10%概率返还 1 个羊毛", 0),
    HEART_OF_STEEL("HEART_OF_STEEL", "§7钢铁之心", "§7受到攻击时获得 2 颗额外黄心护盾(10秒)", 180),
    BLIND_STRIKE("BLIND_STRIKE", "§8致盲术", "§7攻击有15%概率使敌人失明 (5秒)", 60),
    HARD_LANDING("HARD_LANDING", "§b强行着陆", "§7彻底免疫下一次摔落伤害", 45),
    SECOND_ROUND("SECOND_ROUND", "§d第二回合", "§7濒死受击时, 5%概率直接在副手生成不死图腾", 300),
    BARRIER("BARRIER", "§6屏障", "§7血量低于50%受击时, 获得 4 颗额外黄心护盾 (10秒)", 300),
    SHOCKWAVE("SHOCKWAVE", "§6震撼", "§7攻击有15%概率造成巨额击退并将敌人击飞", 90),
    THUNDER_ROAR("THUNDER_ROAR", "§e奔雷怒吼", "§7攻击有10%概率召唤雷电并造成额外 3 点真实伤害", 45),
    MARTYR("MARTYR", "§c同归于尽", "§7死亡时在原地引爆一颗高伤害的TNT", 120),
    OVERLOAD("OVERLOAD", "§e超载", "§7受到攻击时有概率获得急迫1效果(5s)", 30),
    WINDWALKER("WINDWALKER", "§b神行", "§7受到攻击时有概率获得速度2效果(3s)", 30),
    GRAVITY_COLLAPSE("GRAVITY_COLLAPSE", "§5引力崩坏", "§7弓箭命中时有概率使敌人漂浮", 0),
    WAR_STOMP("WAR_STOMP", "§4战争践踏", "§7击杀敌人后获得力量1增益(5s)", 60),
    ASSASSINATION("ASSASSINATION", "§8刺杀", "§7跳劈(暴击)时有极小概率造成双倍伤害", 60);
    public final String id;
    public final String displayName;
    public final String description;
    public final int maxCooldown; // 秒

    BrandType(String id, String displayName, String description, int maxCooldown) {
        this.id = id;
        this.displayName = displayName;
        this.description = description;
        this.maxCooldown = maxCooldown;
    }
}