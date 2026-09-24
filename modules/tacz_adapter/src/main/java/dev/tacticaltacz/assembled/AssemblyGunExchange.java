package dev.tacticaltacz.assembled;

import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.entity.IGunOperator;
import dev.itemfoundation.api.assembly.*;
import dev.firearms.assembly.AssemblyEngine;
import dev.firearms.workbench.WorkbenchInventoryHost;
import dev.firearms.workbench.WorkbenchSwapPlan;
import dev.tacticaltacz.AmmoBridge;
import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Pure proposal over one server inventory quote. Native stored ammunition is refunded atomically with feed-container/capacity changes. */
public final class AssemblyGunExchange {
    public static boolean ready(ServerPlayer p, ItemStack target){
        if(!AssembledWeapons.isGun(target))return false;
        var gun=(IGun)target.getItem();
        if(gun.hasAttachmentLock(target))return false;
        if(!ItemStack.matches(target,p.getMainHandItem()))return true;
        var op=IGunOperator.fromLivingEntity(p);
        return !p.isUsingItem()&&!op.getSynIsBolting()&&op.getSynReloadState().getCountDown()<0&&op.getSynShootCoolDown()<=0&&op.getSynDrawCoolDown()<=0;
    }
    public static Optional<WorkbenchInventoryHost.Change> plan(ItemStack held,ItemStack payment,List<String> path){
        var planned=WorkbenchSwapPlan.plan(new Host(held),held,payment,path);
        if(planned.isEmpty())return Optional.empty();
        try{
            var edit=planned.get();var weapon=AssembledWeapons.from(held);
            var refunds=new ArrayList<ItemStack>();edit.detached().ifPresent(p->refunds.add(p.stack()));
            var changed=edit.changed();
            if(weapon.feed.affectedBy(path)){
                var gun=IGun.getIGunOrNull(changed);int count=gun.getCurrentAmmoCount(changed);
                if(count>0){var ammo=AmmoBridge.ammunition(changed);if(ammo==null)return Optional.empty();refunds.add(new ItemStack(ammo,count));gun.setCurrentAmmoCount(changed,0);}
                // A chambered round remains usable; no physical loaded-magazine claim is made.
            }
            return Optional.of(new WorkbenchInventoryHost.Change(changed,refunds));
        }catch(IllegalArgumentException invalid){return Optional.empty();}
    }
    private static final class Host implements WorkbenchSwapPlan.Parts {
        private final AssembledWeapon weapon;
        Host(ItemStack held){weapon=AssembledWeapons.from(held);}
        public boolean isGun(ItemStack stack){return AssembledWeapons.isGun(stack);}
        public AssemblyEngine engine(){return weapon.ENGINE;}
        public dev.firearms.assembly.AssemblyNode project(ItemStack stack){return weapon.project(stack);}
        public UUID identity(ItemStack stack){return AssembledWeapon.identity(stack);}
    }
    private AssemblyGunExchange(){}
}
