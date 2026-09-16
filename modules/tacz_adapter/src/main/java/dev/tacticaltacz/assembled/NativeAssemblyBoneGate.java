package dev.tacticaltacz.assembled;

import com.tacz.guns.client.model.IFunctionalRenderer;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

/** Additional physical-presence gate. Never overwrites native ammo rules or animated visibility. */
final class NativeAssemblyBoneGate {
    private static final IFunctionalRenderer HIDDEN=(poses,buffer,context,light,overlay)->{};
    static Function<BedrockPart,IFunctionalRenderer> wrap(Function<BedrockPart,IFunctionalRenderer> original,BooleanSupplier present){
        return part->{
            var nativeRenderer=original==null?null:original.apply(part);
            return present.getAsBoolean()?nativeRenderer:HIDDEN;
        };
    }
    private NativeAssemblyBoneGate(){}
}
