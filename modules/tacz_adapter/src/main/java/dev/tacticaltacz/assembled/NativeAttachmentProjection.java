package dev.tacticaltacz.assembled;

import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.attachment.AttachmentType;
import dev.itemfoundation.api.assembly.*;
import java.util.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/** Native attachment access is a view of physical children, never a GunAttachment copy. */
public final class NativeAttachmentProjection {
    private static final List<String> MUZZLE=List.of("upper","barrel_mount","barrel","muzzle");
    public static List<String> path(ItemStack gun,AttachmentType type,ItemStack replacement){
        return switch(type){
            case SCOPE -> List.of("upper","scope");
            case STOCK -> List.of("buffer","stock");
            case GRIP -> List.of("upper","barrel_mount","handguard","grip");
            case LASER -> List.of("upper","barrel_mount","handguard","laser");
            case EXTENDED_MAG -> List.of("magazine");
            case MUZZLE -> {
                var id=IAttachment.getIAttachmentOrNull(replacement);
                boolean bayonet=id!=null&&id.getAttachmentId(replacement).toString().equals("tacz:bayonet_m9");
                var child=at(gun,MUZZLE);boolean existing=AssemblyTrees.state(child).in("bayonet").isPresent();
                yield bayonet||(replacement.isEmpty()&&existing)?List.of("upper","barrel_mount","barrel","muzzle","bayonet"):MUZZLE;
            }
            default -> List.of();
        };
    }
    public static ItemStack at(ItemStack gun,List<String> path){
        if(path.isEmpty())return ItemStack.EMPTY;
        var current=gun;
        for(var slot:path){var child=AssemblyTrees.state(current).in(slot).filter(AssemblyState.Installed::enabled);if(child.isEmpty())return ItemStack.EMPTY;current=child.get().stack();}
        return current;
    }
    public static ItemStack get(ItemStack gun,AttachmentType type){
        var part=at(gun,path(gun,type,ItemStack.EMPTY));
        return IAttachment.getIAttachmentOrNull(part)==null?ItemStack.EMPTY:part.copy();
    }
    public static boolean allows(ItemStack gun,ItemStack candidate){
        var weapon=AssembledWeapons.from(gun);var attachment=IAttachment.getIAttachmentOrNull(candidate);
        if(weapon==null||attachment==null||!weapon.isPart(candidate))return false;
        try{
            var path=path(gun,attachment.getType(candidate),candidate);var tree=weapon.project(gun);
            var replacement=weapon.project(weapon.proposalPart(candidate,UUID.randomUUID()));
            return (at(gun,path).isEmpty()?weapon.ENGINE.install(tree,path,replacement):weapon.ENGINE.replace(tree,path,replacement)).success();
        }catch(IllegalArgumentException invalid){return false;}
    }
    public static void setTag(ItemStack gun,AttachmentType type,CompoundTag tag){
        var path=path(gun,type,ItemStack.EMPTY);var part=get(gun,type);if(part.isEmpty()||tag==null)return;
        var old=part.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();
        // Native controls may change their own state, never replace physical identity or model.
        for(var key:List.of("ZoomNumber","LaserColor"))if(tag.contains(key))old.putInt(key,key.equals("ZoomNumber")?Math.max(0,tag.getInt(key)):tag.getInt(key)&0xFFFFFF);
        part.set(DataComponents.CUSTOM_DATA,CustomData.of(old));
        var changed=AssemblyTrees.replace(gun,path,part);
        gun.set(AssemblyComponents.STATE.get(),AssemblyTrees.state(changed));
    }
    public static boolean blocksAim(ItemStack gun){var weapon=AssembledWeapons.from(gun);return weapon!=null&&weapon.nativeRig&&!hasSight(gun);}
    public static boolean hasSight(ItemStack gun){
        return !get(gun,AttachmentType.SCOPE).isEmpty()||(!at(gun,List.of("upper","rear_sight")).isEmpty()&&!at(gun,List.of("upper","barrel_mount","barrel","gas","front_sight")).isEmpty());
    }
    private NativeAttachmentProjection(){}
}
