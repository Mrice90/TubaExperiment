package com.infiniteconquest.core;

import java.util.Objects;
import java.util.UUID;

public final class CardInstance {
    private final UUID instanceId;
    private final CardDefinition definition;
    private final int owner;
    private Zone zone;
    private int damage;
    private boolean tapped;
    private int movementSpent;
    private boolean attackedThisTurn;
    private boolean blinkUsedThisTurn;
    private int attackBonus;
    private int defenseBonus;
    private int combatDamage;
    private boolean abilityUsedThisTurn;

    public CardInstance(UUID instanceId, CardDefinition definition, int owner, Zone zone) {
        this.instanceId = Objects.requireNonNull(instanceId);
        this.definition = Objects.requireNonNull(definition);
        if (owner < 0 || owner > 1) throw new IllegalArgumentException("Owner must be player 0 or 1");
        this.owner = owner;
        this.zone = Objects.requireNonNull(zone);
    }

    public UUID instanceId() { return instanceId; }
    public CardDefinition definition() { return definition; }
    public int owner() { return owner; }
    public Zone zone() { return zone; }
    public int damage() { return damage; }
    public boolean tapped() { return tapped; }
    public int movementSpent() { return movementSpent; }
    public int movementRemaining() { return Math.max(0, definition.movement() - movementSpent); }
    public boolean attackedThisTurn() { return attackedThisTurn; }
    public boolean blinkUsedThisTurn() { return blinkUsedThisTurn; }
    public int effectiveAttack() { return definition.attack() + attackBonus; }
    public int effectiveDefense() { return definition.defense() + defenseBonus; }
    public int attackBonus() { return attackBonus; }
    public int defenseBonus() { return defenseBonus; }
    /** Damage marked on a Character by combat during the current turn. */
    public int combatDamage() { return combatDamage; }
    public int defenseRemaining() { return Math.max(0, effectiveDefense() - combatDamage); }
    public boolean abilityUsedThisTurn() { return abilityUsedThisTurn; }
    public void moveTo(Zone newZone) { zone = Objects.requireNonNull(newZone); }
    public void addDamage(int amount) {
        if (amount < 0) throw new IllegalArgumentException("Damage cannot be negative");
        damage += amount;
    }
    public void setTapped(boolean value) { tapped = value; }
    public void healDamage(int amount) {
        if (amount < 0) throw new IllegalArgumentException("Healing cannot be negative");
        damage = Math.max(0, damage - amount);
    }
    public void addAttackBonus(int amount) {
        if (amount < 0) throw new IllegalArgumentException("Bonus cannot be negative");
        attackBonus += amount;
    }
    public void addDefenseBonus(int amount) {
        if (amount < 0) throw new IllegalArgumentException("Bonus cannot be negative");
        defenseBonus += amount;
    }
    public void addCombatDamage(int amount) {
        if (amount < 0) throw new IllegalArgumentException("Combat damage cannot be negative");
        combatDamage += amount;
    }
    public void spendMovement(int amount) {
        if (amount < 0 || amount > movementRemaining()) throw new IllegalArgumentException("Insufficient movement");
        movementSpent += amount;
    }
    public void restoreMovement(int amount) {
        if (amount < 0) throw new IllegalArgumentException("Movement restoration cannot be negative");
        movementSpent = Math.max(0, movementSpent - amount);
    }
    public void markAttacked() { attackedThisTurn = true; }
    public void markBlinkUsed() { blinkUsedThisTurn = true; }
    public void markAbilityUsed() { abilityUsedThisTurn = true; }
    public void resetTurnActions() {
        movementSpent = 0;
        attackedThisTurn = false;
        blinkUsedThisTurn = false;
        abilityUsedThisTurn = false;
        attackBonus = 0;
        defenseBonus = 0;
        tapped = false;
    }
    public void healCombatDamage(int amount) {
        if(amount<0)throw new IllegalArgumentException("Healing cannot be negative");
        combatDamage=Math.max(0,combatDamage-amount);
    }
    public void clearCombatDamage() { combatDamage = 0; }
}
