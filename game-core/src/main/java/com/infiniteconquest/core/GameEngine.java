package com.infiniteconquest.core;

import com.infiniteconquest.data.Keyword;

import java.util.*;

public final class GameEngine {
    private final MovementRules movementRules = new MovementRules();
    private final LineOfSightRules lineOfSightRules = new LineOfSightRules();
    private final CardAbilityRules cardAbilityRules = new CardAbilityRules();

    public ActionResult apply(GameState state, GameAction action) {
        Objects.requireNonNull(state); Objects.requireNonNull(action);
        if (state.phase() != Phase.PLAY) return ActionResult.rejected("Actions require Play phase");
        if (action instanceof GameAction.CastSpell a) return castSpell(state, a);
        if (action.playerId() != state.activePlayer()) return ActionResult.rejected("Not active player");
        if (action instanceof GameAction.EndTurn) { state.advanceTurn(); return ActionResult.accepted("Turn ended"); }
        if (action instanceof GameAction.PlayLand a) return playLand(state, a);
        if (action instanceof GameAction.PlayStructure a) return playStructure(state, a);
        if (action instanceof GameAction.SummonCharacter a) return summonCharacter(state, a);
        if (action instanceof GameAction.BurrowCharacter a) return burrowCharacter(state, a);
        if (action instanceof GameAction.MoveCharacter a) return moveCharacter(state, a);
        if (action instanceof GameAction.BlinkCharacter a) return blinkCharacter(state, a);
        if (action instanceof GameAction.Attack a) return attack(state, a);
        if (action instanceof GameAction.ActivateAbility a) return activateAbility(state, a);
        return ActionResult.rejected("Unsupported action");
    }

    private ActionResult activateAbility(GameState state, GameAction.ActivateAbility action) {
        CardInstance source = state.card(action.cardId()).orElse(null);
        if (source == null || source.owner() != action.playerId() || source.zone() != Zone.BATTLEFIELD) {
            return ActionResult.rejected("Ability source must be your battlefield card");
        }
        BoardPosition position = state.board().positionOf(source.instanceId()).orElse(null);
        if (position == null || !state.board().topAt(position).orElseThrow().equals(source.instanceId())) {
            return ActionResult.rejected("Only the top card of a stack can activate an ability");
        }
        List<CardAbility> abilities = cardAbilityRules.abilities(source, AbilityTrigger.ACTIVATED);
        if (abilities.isEmpty()) return ActionResult.rejected("Card has no activated ability");
        if (source.abilityUsedThisTurn()) return ActionResult.rejected("Ability already used this turn");
        int totalCost = abilities.stream().mapToInt(CardAbility::gpCost).sum();
        if (state.player(action.playerId()).currentGp() < totalCost) return ActionResult.rejected("Not enough GP");
        state.spendGp(action.playerId(), totalCost, source.definition().name() + " ability");
        source.markAbilityUsed();
        abilities.forEach(ability -> cardAbilityRules.resolve(state, source, ability));
        return ActionResult.accepted("Activated ability resolved");
    }

    public Set<BoardPosition> legalMovementDestinations(GameState state, UUID id) {
        return state.card(id).map(c -> movementRules.legalDestinations(state, c)).orElse(Set.of());
    }

    public Set<BoardPosition> legalAttackDestinations(GameState state, UUID attackerId) {
        CardInstance attacker = state.card(attackerId).orElse(null);
        if (attacker == null || attacker.owner() != state.activePlayer()
                || attacker.definition().type() != CardType.CHARACTER || attacker.attackedThisTurn()) {
            return Set.of();
        }
        BoardPosition from = state.board().positionOf(attacker.instanceId()).orElse(null);
        if (from == null || !state.board().topAt(from).orElseThrow().equals(attacker.instanceId())) {
            return Set.of();
        }
        Set<BoardPosition> legal = new LinkedHashSet<>();
        for (BoardPosition to : state.board().positions()) {
            Optional<UUID> targetId = state.board().topAt(to);
            if (targetId.isEmpty()) continue;
            CardInstance target = state.card(targetId.orElseThrow()).orElseThrow();
            if (target.owner() != attacker.owner()
                    && (target.definition().type() == CardType.CHARACTER || target.definition().isPermanent())
                    && state.rules().geometry().distance(from, to) <= effectiveRange(state, attacker)
                    && lineOfSightRules.hasLineOfSight(state, from, to)) {
                legal.add(to);
            }
        }
        return Collections.unmodifiableSet(legal);
    }

    private ActionResult castSpell(GameState state, GameAction.CastSpell action) {
        CardInstance spell = playableFromHand(state, action.playerId(), action.cardId(), CardType.SPELL);
        if (spell == null) return ActionResult.rejected("Spell must be owned, affordable, and in hand");

        CardInstance target = action.targetId() == null ? null : state.card(action.targetId()).orElse(null);
        for (SpellEffect effect : spell.definition().effects()) {
            String error = validateSpellEffect(state, action.playerId(), effect, target, action.destination());
            if (error != null) return ActionResult.rejected(error);
        }

        payAndRemoveFromHand(state, spell);
        spell.moveTo(Zone.DISCARD);
        state.player(action.playerId()).addToDiscard(spell.instanceId());
        state.recordCardPlayed(spell);
        for (SpellEffect effect : spell.definition().effects()) {
            applySpellEffect(state, action.playerId(), effect, target, action.destination());
            if (state.phase() == Phase.GAME_OVER) break;
        }
        return ActionResult.accepted(action.playerId() == state.activePlayer()
                ? "Spell resolved" : "Reaction spell resolved");
    }

    private String validateSpellEffect(GameState state, int caster, SpellEffect effect,
                                       CardInstance target, BoardPosition destination) {
        boolean targetRequired = true;
        if (target == null) return "Spell requires a target";
        {
            BoardPosition position = state.board().positionOf(target.instanceId()).orElse(null);
            if (position == null || !state.board().topAt(position).orElseThrow().equals(target.instanceId())) {
                return "Spell can target only the top battlefield card";
            }
            if (effect.target() == SpellTarget.FRIENDLY && target.owner() != caster) return "Spell requires a friendly target";
            if (effect.target() == SpellTarget.ENEMY && target.owner() == caster) return "Spell requires an enemy target";
        }
        return switch (effect.type()) {
            case STRIKE_CHARACTER, RETURN_CHARACTER, BUFF_ATTACK, BUFF_DEFENSE, TELEPORT_CHARACTER ->
                    target.definition().type() != CardType.CHARACTER ? "Spell requires a Character target"
                            : effect.type() == SpellEffectType.TELEPORT_CHARACTER
                            && (destination == null || !state.board().isEmpty(destination))
                            ? "Teleport requires an empty destination" : null;
            case DAMAGE_PERMANENT, HEAL_PERMANENT ->
                    !target.definition().isPermanent() ? "Spell requires a Permanent target" : null;
        };
    }

    private void applySpellEffect(GameState state, int casterId, SpellEffect effect,
                                  CardInstance target, BoardPosition destination) {
        switch (effect.type()) {
            case STRIKE_CHARACTER -> {
                if (effect.amount() >= target.effectiveDefense()) state.destroy(target);
            }
            case DAMAGE_PERMANENT -> {
                target.addDamage(effect.amount());
                if (target.damage() >= target.definition().hitPoints()) state.destroy(target);
            }
            case HEAL_PERMANENT -> target.healDamage(effect.amount());
            case TELEPORT_CHARACTER -> {
                BoardPosition origin = state.board().positionOf(target.instanceId()).orElseThrow();
                state.board().moveTop(origin, destination, target.instanceId());
                state.recordCharacterMoved(target, origin, destination, 0);
                TerrainRules.entered(state,target,origin,destination,false);
            }
            case RETURN_CHARACTER -> {
                state.returnCharacterToHand(target);
                new CapitalPassiveRules().onCharacterReturnedBySpell(state, casterId, target);
            }
            case BUFF_ATTACK -> target.addAttackBonus(effect.amount());
            case BUFF_DEFENSE -> target.addDefenseBonus(effect.amount());
        }
    }

    private ActionResult summonCharacter(GameState state, GameAction.SummonCharacter action) {
        CardInstance card = playableFromHand(state, action.playerId(), action.cardId(), CardType.CHARACTER);
        if (card == null) return ActionResult.rejected("Character must be owned, affordable, and in hand");
        BoardPosition destination = action.destination();
        boolean onFriendlyCard = state.board().stackAt(destination).stream()
                .map(id -> state.card(id).orElseThrow())
                .allMatch(c -> c.owner() == action.playerId());
        boolean adjacentToFriendlyPermanent = state.board().positions().stream()
                .filter(p -> state.rules().geometry().adjacent(destination, p))
                .flatMap(p -> state.board().stackAt(p).stream())
                .map(id -> state.card(id).orElseThrow())
                .anyMatch(c -> c.owner() == action.playerId() && c.definition().isPermanent());
        if ((!state.board().isEmpty(destination) && !onFriendlyCard)
                || (state.board().isEmpty(destination) && !adjacentToFriendlyPermanent)) {
            return ActionResult.rejected("Character must join a friendly stack or deploy within one space of a friendly Permanent");
        }
        payAndRemoveFromHand(state, card);
        card.moveTo(Zone.BATTLEFIELD);
        state.board().push(destination, card.instanceId());
        state.recordCardPlayed(card);
        TerrainRules.entered(state,card,null,destination,true);
        return ActionResult.accepted("Character summoned");
    }

    private ActionResult burrowCharacter(GameState state, GameAction.BurrowCharacter action) {
        CardInstance card = playableFromHand(state, action.playerId(), action.cardId(), CardType.CHARACTER);
        if (card == null || !card.definition().hasKeyword(Keyword.MOLE)) {
            return ActionResult.rejected("Only an affordable Mole Character in hand can burrow");
        }
        Optional<UUID> top = state.board().topAt(action.destination());
        if (top.isEmpty()) return ActionResult.rejected("Mole requires a controlled Land");
        CardInstance land = state.card(top.orElseThrow()).orElseThrow();
        if (land.owner() != action.playerId() || land.definition().type() != CardType.LAND) {
            return ActionResult.rejected("Mole requires a controlled Land on top of the stack");
        }
        payAndRemoveFromHand(state, card);
        card.moveTo(Zone.BATTLEFIELD);
        state.board().insertBelowTop(action.destination(), card.instanceId());
        state.recordCardPlayed(card);
        new CapitalPassiveRules().onBurrowed(state, card);
        return ActionResult.accepted("Mole burrowed beneath Land");
    }

    private ActionResult playStructure(GameState state, GameAction.PlayStructure action) {
        if (!state.canPlayDevelopment(action.playerId(), CardType.STRUCTURE)) {
            return ActionResult.rejected("Only one Structure may be played per turn");
        }
        CardInstance card = developableFromHand(state, action.playerId(), action.cardId(), CardType.STRUCTURE);
        if (card == null) return ActionResult.rejected("Structure must be in hand and its turn value must be reached and its gold cost affordable");
        Optional<UUID> top = state.board().topAt(action.destination());
        if (top.isEmpty()) return ActionResult.rejected("Structure requires a controlled Land");
        CardInstance foundation = state.card(top.get()).orElseThrow();
        if (foundation.owner() != action.playerId() || foundation.definition().type() != CardType.LAND) {
            return ActionResult.rejected("Structure requires a controlled Land on top of the stack");
        }
        removeDevelopmentFromHand(state, card);
        card.moveTo(Zone.BATTLEFIELD);
        state.board().push(action.destination(), card.instanceId());
        state.recordCardPlayed(card);
        return ActionResult.accepted("Structure played");
    }

    private ActionResult blinkCharacter(GameState state, GameAction.BlinkCharacter action) {
        CardInstance card = state.card(action.cardId()).orElse(null);
        if (card == null || card.owner() != action.playerId() || card.definition().type() != CardType.CHARACTER
                || !card.definition().hasKeyword(Keyword.BLINK)) {
            return ActionResult.rejected("Invalid Blink Character");
        }
        if (card.blinkUsedThisTurn()) return ActionResult.rejected("Blink already used this turn");
        BoardPosition origin = state.board().positionOf(card.instanceId()).orElse(null);
        if (origin == null || !state.board().topAt(origin).orElseThrow().equals(card.instanceId())) {
            return ActionResult.rejected("Only the top Character can Blink");
        }
        if (!state.board().isEmpty(action.destination())) {
            return ActionResult.rejected("Blink destination must be empty");
        }
        state.board().moveTop(origin, action.destination(), card.instanceId());
        card.markBlinkUsed();
        state.recordCharacterMoved(card, origin, action.destination(), 0);
        TerrainRules.entered(state,card,origin,action.destination(),false);
        if(card.zone()==Zone.BATTLEFIELD)new CapitalPassiveRules().onBlinked(state, card);
        return ActionResult.accepted("Character Blinked");
    }

    private ActionResult attack(GameState state, GameAction.Attack action) {
        CardInstance attacker = state.card(action.attackerId()).orElse(null);
        CardInstance target = state.card(action.targetId()).orElse(null);
        if (attacker == null || target == null) return ActionResult.rejected("Unknown attacker or target");
        if (attacker.owner() != action.playerId() || target.owner() == action.playerId()) return ActionResult.rejected("Invalid ownership");
        if (attacker.definition().type() != CardType.CHARACTER || attacker.attackedThisTurn()) return ActionResult.rejected("Attacker cannot attack");
        BoardPosition from = state.board().positionOf(attacker.instanceId()).orElse(null);
        BoardPosition to = state.board().positionOf(target.instanceId()).orElse(null);
        if (from == null || to == null) return ActionResult.rejected("Attacker and target must be on battlefield");
        if (!state.board().topAt(from).orElseThrow().equals(attacker.instanceId())
                || !state.board().topAt(to).orElseThrow().equals(target.instanceId())) return ActionResult.rejected("Only top cards interact");
        if (state.rules().geometry().distance(from, to) > effectiveRange(state, attacker)) return ActionResult.rejected("Target out of range");
        if (!lineOfSightRules.hasLineOfSight(state, from, to)) return ActionResult.rejected("Line of sight blocked");

        new CapitalPassiveRules().beforeAttack(state, attacker, target);
        attacker.markAttacked();
        state.recordAttack(attacker, target);
        if (target.definition().type() == CardType.CHARACTER) {
            int attackerPower = effectiveAttack(state, attacker);
            int defenderPower = effectiveAttack(state, target);
            target.addCombatDamage(TerrainRules.reduceDamage(state,target,attackerPower,state.rules().geometry().distance(from,to)>1));
            boolean targetDies = target.combatDamage() >= target.effectiveDefense();
            boolean fastStrikeStopsRetaliation = attacker.definition().hasKeyword(Keyword.FAST_STRIKE)
                    && attackerPower > target.effectiveDefense();
            boolean canRetaliate = !fastStrikeStopsRetaliation && defenderPower > 0
                    && state.rules().geometry().distance(to, from) <= effectiveRange(state, target)
                    && lineOfSightRules.hasLineOfSight(state, to, from);
            if (canRetaliate) attacker.addCombatDamage(TerrainRules.reduceDamage(state,attacker,defenderPower,state.rules().geometry().distance(to,from)>1));
            boolean attackerDies = canRetaliate && attacker.combatDamage() >= attacker.effectiveDefense();
            if (targetDies) state.destroy(target);
            if (attackerDies) state.destroy(attacker);
            if (targetDies && attackerDies) return ActionResult.accepted("Both Characters destroyed in simultaneous combat");
            if (targetDies) return ActionResult.accepted("Defender destroyed");
            if (attackerDies) return ActionResult.accepted("Attacker destroyed by retaliation");
            return ActionResult.accepted(canRetaliate
                    ? "Combat damage marked until end of turn"
                    : "Combat damage marked; defender could not retaliate at this range");
        } else if (target.definition().isPermanent()) {
            int damage = effectiveAttack(state, attacker);
            if (attacker.definition().hasKeyword(Keyword.SIEGE)) damage *= 2;
            target.addDamage(TerrainRules.reduceDamage(state,target,damage,state.rules().geometry().distance(from,to)>1));
            if (target.damage() >= target.definition().hitPoints()) state.destroy(target);
        } else return ActionResult.rejected("Target cannot be attacked");
        return ActionResult.accepted("Attack resolved");
    }

    private ActionResult moveCharacter(GameState state, GameAction.MoveCharacter action) {
        CardInstance card = state.card(action.cardId()).orElse(null);
        if (card == null || card.owner() != action.playerId() || card.definition().type() != CardType.CHARACTER)
            return ActionResult.rejected("Invalid Character");
        List<BoardPosition> path = movementRules.shortestLegalPath(state, card, action.destination());
        if (path.isEmpty()) return ActionResult.rejected("Destination unreachable");
        BoardPosition origin = state.board().positionOf(card.instanceId()).orElseThrow();
        BoardPosition current = origin;
        Set<UUID> reacted = new HashSet<>();
        int traveled = 0;
        int opportunityAttacks = 0;
        for (OpportunityThreat threat : opportunityThreatsAt(state, card, origin, reacted)) {
            CardInstance enemy = state.card(threat.attackerId()).orElseThrow();
            reacted.add(enemy.instanceId());
            opportunityAttacks++;
            state.recordOpportunityAttack(enemy, card, origin);
            card.addCombatDamage(TerrainRules.reduceDamage(state,card,effectiveAttack(state,enemy),state.rules().geometry().distance(threat.attackerPosition(),origin)>1));
            if (card.combatDamage() >= card.effectiveDefense()) {
                state.destroy(card);
                break;
            }
        }
        for (BoardPosition step : path) {
            if (card.zone() != Zone.BATTLEFIELD) break;
            state.board().moveTop(current, step, card.instanceId());
            BoardPosition previous=current;
            current = step;
            traveled++;
            card.spendMovement(1);
            TerrainRules.entered(state,card,previous,step,false);
            if(card.zone()!=Zone.BATTLEFIELD)break;
            for (OpportunityThreat threat : opportunityThreatsAt(state, card, step, reacted)) {
                CardInstance enemy = state.card(threat.attackerId()).orElseThrow();
                reacted.add(enemy.instanceId());
                opportunityAttacks++;
                state.recordOpportunityAttack(enemy, card, step);
                card.addCombatDamage(TerrainRules.reduceDamage(state,card,effectiveAttack(state,enemy),state.rules().geometry().distance(threat.attackerPosition(),step)>1));
                if (card.combatDamage() >= card.effectiveDefense()) {
                    state.destroy(card);
                    break;
                }
            }
            if (card.zone() != Zone.BATTLEFIELD) break;
        }
        state.recordCharacterMoved(card, origin, current, traveled);
        if (card.zone() == Zone.BATTLEFIELD) new CapitalPassiveRules().onMoved(state, card);
        if (card.zone() != Zone.BATTLEFIELD) {
            return ActionResult.accepted("Movement stopped: Character destroyed by an entry effect or opportunity attack");
        }
        return ActionResult.accepted(opportunityAttacks == 0 ? "Character moved"
                : "Character moved through " + opportunityAttacks + " opportunity attack" + (opportunityAttacks == 1 ? "" : "s"));
    }

    public List<OpportunityThreat> opportunityThreats(GameState state, UUID moverId, BoardPosition destination) {
        CardInstance mover = state.card(moverId).orElse(null);
        if (mover == null) return List.of();
        List<BoardPosition> path = movementRules.shortestLegalPath(state, mover, destination);
        if (path.isEmpty()) return List.of();
        Set<UUID> found = new LinkedHashSet<>();
        List<OpportunityThreat> threats = new ArrayList<>();
        BoardPosition origin = state.board().positionOf(mover.instanceId()).orElseThrow();
        List<BoardPosition> threatenedSteps = new ArrayList<>();
        threatenedSteps.add(origin);
        threatenedSteps.addAll(path);
        for (BoardPosition step : threatenedSteps) {
            for (OpportunityThreat threat : opportunityThreatsAt(state, mover, step, found)) {
                found.add(threat.attackerId());
                threats.add(threat);
            }
        }
        return List.copyOf(threats);
    }

    private List<OpportunityThreat> opportunityThreatsAt(GameState state, CardInstance mover,
                                                          BoardPosition step, Set<UUID> excluded) {
        List<OpportunityThreat> threats = new ArrayList<>();
        for (BoardPosition enemyPosition : state.board().positions()) {
            Optional<UUID> top = state.board().topAt(enemyPosition);
            if (top.isEmpty() || excluded.contains(top.get()) || top.get().equals(mover.instanceId())) continue;
            CardInstance enemy = state.card(top.get()).orElseThrow();
            if (enemy.owner() == mover.owner() || enemy.definition().type() != CardType.CHARACTER
                    || effectiveAttack(state, enemy) <= 0 || state.rules().geometry().distance(enemyPosition, step) > effectiveRange(state, enemy)) continue;
            if (lineOfSightRules.hasLineOfSight(state, enemyPosition, step)) {
                threats.add(new OpportunityThreat(enemy.instanceId(), enemyPosition, step,
                        enemy.definition().name(), effectiveAttack(state, enemy), mover.defenseRemaining()));
            }
        }
        return threats;
    }

    public record OpportunityThreat(UUID attackerId, BoardPosition attackerPosition, BoardPosition triggerPosition,
                                    String attackerName, int attack, int moverDefense) {
        public boolean lethal() { return attack >= moverDefense; }
    }

    public int effectiveAttack(GameState state, CardInstance card) {
        return card.effectiveAttack() + (sharpShotActive(state, card) ? 1 : 0);
    }

    public int effectiveRange(GameState state, CardInstance card) {
        return card.definition().range() + (sharpShotActive(state, card) ? 1 : 0) + TerrainRules.rangeBonus(state,card);
    }

    private boolean sharpShotActive(GameState state, CardInstance card) {
        if (!card.definition().hasKeyword(Keyword.SHARP_SHOT)) return false;
        BoardPosition position = state.board().positionOf(card.instanceId()).orElse(null);
        if (position == null || !state.board().topAt(position).orElse(null).equals(card.instanceId())) return false;
        return state.board().stackAt(position).stream()
                .takeWhile(id -> !id.equals(card.instanceId()))
                .map(id -> state.card(id).orElseThrow())
                .anyMatch(under -> under.owner() == card.owner()
                        && (under.definition().type() == CardType.STRUCTURE
                        || under.definition().type() == CardType.CAPITAL));
    }

    private ActionResult playLand(GameState state, GameAction.PlayLand action) {
        if (!state.canPlayDevelopment(action.playerId(), CardType.LAND)) {
            return ActionResult.rejected("Only one Land may be played per turn");
        }
        CardInstance card = developableFromHand(state, action.playerId(), action.cardId(), CardType.LAND);
        if (card == null) return ActionResult.rejected("Land must be in hand and its turn value must be reached and its gold cost affordable");
        if (!action.destination().isOnPlayerSide(action.playerId()) || !state.board().isEmpty(action.destination()))
            return ActionResult.rejected("Land requires an empty space on owner's plot");
        removeDevelopmentFromHand(state, card);
        card.moveTo(Zone.BATTLEFIELD);
        state.board().push(action.destination(), card.instanceId());
        state.recordCardPlayed(card);
        return ActionResult.accepted("Land played");
    }

    private CardInstance playableFromHand(GameState state, int playerId, UUID id, CardType type) {
        CardInstance card = state.card(id).orElse(null);
        if (card == null || card.owner() != playerId || card.definition().type() != type
                || card.zone() != Zone.HAND || !state.player(playerId).hasInHand(id)
                || card.definition().goldCost() > state.player(playerId).currentGp()) return null;
        return card;
    }
    private CardInstance developableFromHand(GameState state, int playerId, UUID id, CardType type) {
        CardInstance card = state.card(id).orElse(null);
        if (card == null || card.owner() != playerId || card.definition().type() != type
                || card.zone() != Zone.HAND || !state.player(playerId).hasInHand(id)
                || state.personalTurnNumber(playerId) < card.definition().cost()
                || state.player(playerId).currentGp() < card.definition().developmentGoldCost()) return null;
        return card;
    }
    private void removeDevelopmentFromHand(GameState state, CardInstance card) {
        state.spendGp(card.owner(),card.definition().developmentGoldCost(),card.definition().name());
        state.player(card.owner()).removeFromHand(card.instanceId());
    }
    private void payAndRemoveFromHand(GameState state, CardInstance card) {
        state.spendGp(card.owner(), card.definition().cost(), card.definition().name());
        state.player(card.owner()).removeFromHand(card.instanceId());
    }
}
