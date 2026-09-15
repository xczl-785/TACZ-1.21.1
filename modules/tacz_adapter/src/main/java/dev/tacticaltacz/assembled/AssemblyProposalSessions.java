package dev.tacticaltacz.assembled;

import dev.tacticalinventory.api.TacticalHeldExchange;
import java.util.*;

/** One short-lived server proposal per player, shared by native refit and the workbench. */
final class AssemblyProposalSessions {
    record Session(UUID token,TacticalHeldExchange.View view,long expires){}
    private final Map<UUID,Session> sessions=new HashMap<>();
    Session peek(UUID player){return sessions.get(player);}
    void remove(UUID player){sessions.remove(player);}
    Session take(UUID player,UUID token,long now){
        var session=sessions.remove(player);
        return session!=null&&session.token().equals(token)&&now<=session.expires()?session:null;
    }
    Session issue(UUID player,TacticalHeldExchange.View view,long now){
        sessions.values().removeIf(s->now>s.expires());
        var session=new Session(UUID.randomUUID(),view,Math.addExact(now,1200));sessions.put(player,session);return session;
    }
}
