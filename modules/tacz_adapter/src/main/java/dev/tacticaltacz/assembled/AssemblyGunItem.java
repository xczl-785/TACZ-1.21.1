package dev.tacticaltacz.assembled;

import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.attachment.AttachmentType;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public final class AssemblyGunItem extends com.tacz.guns.item.ModernKineticGunItem {
    private final AssembledWeapon weapon;
    public AssemblyGunItem(AssembledWeapon weapon){this.weapon=weapon;}
    public AssembledWeapon weapon(){return weapon;}
    @Override public ItemStack getDefaultInstance(){return weapon.preset();}
    @Override public ItemStack getAttachment(HolderLookup.Provider provider,ItemStack gun,AttachmentType type){return weapon.nativeRig?NativeAttachmentProjection.get(gun,type):super.getAttachment(provider,gun,type);}
    @Override public ResourceLocation getAttachmentId(ItemStack gun,AttachmentType type){
        if(!weapon.nativeRig)return super.getAttachmentId(gun,type);
        var part=NativeAttachmentProjection.get(gun,type);var attachment=IAttachment.getIAttachmentOrNull(part);
        return attachment==null?DefaultAssets.EMPTY_ATTACHMENT_ID:attachment.getAttachmentId(part);
    }
    @Override public CompoundTag getAttachmentTag(ItemStack gun,AttachmentType type){
        if(!weapon.nativeRig)return super.getAttachmentTag(gun,type);
        var part=NativeAttachmentProjection.get(gun,type);return part.isEmpty()?null:part.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();
    }
    @Override public void setAttachmentTag(ItemStack gun,AttachmentType type,CompoundTag tag){if(weapon.nativeRig)NativeAttachmentProjection.setTag(gun,type,tag);else super.setAttachmentTag(gun,type,tag);}
    @Override public boolean allowAttachment(ItemStack gun,ItemStack attachment){return weapon.nativeRig?NativeAttachmentProjection.allows(gun,attachment):super.allowAttachment(gun,attachment);}
    @Override public void installAttachment(HolderLookup.Provider provider,ItemStack gun,ItemStack attachment){
        if(weapon.nativeRig)throw new IllegalStateException("Physical attachments require an assembly exchange");
        super.installAttachment(provider,gun,attachment);
    }
    @Override public void unloadAttachment(HolderLookup.Provider provider,ItemStack gun,AttachmentType type){
        if(weapon.nativeRig)throw new IllegalStateException("Physical attachments require an assembly exchange");
        super.unloadAttachment(provider,gun,type);
    }
}
