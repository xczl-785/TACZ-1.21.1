package dev.tacticaltacz.assembled;

import java.util.LinkedHashMap;
import java.util.function.LongSupplier;

/** Bounded, expiring negative cache. Never stores a replacement under a successful appearance key. */
final class NativeIconFailures {
    private final int limit;
    private final long retryNanos;
    private final LongSupplier clock;
    private final LinkedHashMap<String,Long> failed=new LinkedHashMap<>();
    private boolean warned;
    private long lastWarning;
    NativeIconFailures(int limit,long retryNanos,LongSupplier clock){this.limit=limit;this.retryNanos=retryNanos;this.clock=clock;}
    boolean blocked(String key){
        var time=failed.get(key);if(time==null)return false;
        if(clock.getAsLong()-time<retryNanos)return true;
        failed.remove(key);return false;
    }
    boolean record(String key){
        long now=clock.getAsLong();failed.put(key,now);
        while(failed.size()>limit)failed.remove(failed.keySet().iterator().next());
        if(warned&&now-lastWarning<retryNanos)return false;
        warned=true;lastWarning=now;return true;
    }
    void clear(){failed.clear();warned=false;}
}
