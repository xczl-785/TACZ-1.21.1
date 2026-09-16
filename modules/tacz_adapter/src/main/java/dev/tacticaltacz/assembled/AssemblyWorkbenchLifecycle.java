package dev.tacticaltacz.assembled;

import java.util.Optional;
import java.util.UUID;

/** Client UI ownership only. A reply cannot cross a screen or temporary-edit boundary. */
final class AssemblyWorkbenchLifecycle {
    private enum Mode { CLOSED, OPENING, REAL, PRESET }
    private Mode mode=Mode.CLOSED;
    private Object owner;
    private UUID request;
    UUID open(Object currentScreen) {
        mode=Mode.OPENING;owner=currentScreen;request=UUID.randomUUID();return request;
    }
    Optional<UUID> exchange(Object currentScreen) {
        if(mode!=Mode.REAL||owner!=currentScreen||pending())return Optional.empty();
        request=UUID.randomUUID();return Optional.of(request);
    }
    boolean receive(UUID id,Object currentScreen) {
        if((mode!=Mode.REAL&&mode!=Mode.OPENING)||owner!=currentScreen||request==null||!request.equals(id))return false;
        request=null;return true;
    }
    void real(Object screen) {mode=Mode.REAL;owner=screen;request=null;}
    boolean preset(Object currentScreen,Object presetScreen) {
        if(mode!=Mode.REAL||owner!=currentScreen||pending())return false;
        mode=Mode.PRESET;owner=presetScreen;request=null;return true;
    }
    boolean watch(Object currentScreen) {
        if(mode!=Mode.CLOSED&&owner!=currentScreen)close();
        return mode!=Mode.CLOSED;
    }
    void close(){mode=Mode.CLOSED;owner=null;request=null;}
    boolean pending(){return request!=null;}
    boolean opening(){return mode==Mode.OPENING;}
    boolean temporary(){return mode==Mode.PRESET;}
}
