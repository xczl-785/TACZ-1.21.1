package dev.tacticaltacz.assembled;

import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.entity.IGunOperator;
import dev.itemfoundation.api.assembly.*;
import dev.firearms.workbench.WorkbenchInventoryHost;
import dev.tacticaltacz.AmmoBridge;
import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Pure proposal over one server inventory quote. Native stored ammunition is refunded atomically with feed-container/capacity changes. */
public final class AssemblyGunExchange {
    public static boolean ready(ServerPlayer p){
        if(!AssembledWeapons.isGun(p.getMainHandItem()))return false;
        var op=IGunOperator.fromLivingEntity(p);
        return !((IGun)p.getMainHandItem().getItem()).hasAttachmentLock(p.getMainHandItem())&&!p.isUsingItem()&&!op.getSynIsBolting()&&op.getSynReloadState().getCountDown()<0&&op.getSynShootCoolDown()<=0&&op.getSynDrawCoolDown()<=0;
    }
    public static Optional<WorkbenchInventoryHost.Change> plan(ItemStack held,ItemStack payment,List<String> path){
        if(!AssembledWeapons.isGun(held)||path.isEmpty()||path.size()>AssemblyTrees.MAX_DEPTH)return Optional.empty();
        var weapon=AssembledWeapons.from(held);
        try{
            var tree=weapon.project(held);
            var parentPath=path.subList(0,path.size()-1);var slot=path.getLast();var parent=AssemblyTrees.at(held,parentPath);var state=AssemblyTrees.state(parent);var old=state.in(slot);
            var result=payment.isEmpty()?weapon.ENGINE.remove(tree,path):old.isPresent()?weapon.ENGINE.replace(tree,path,weapon.project(payment)):weapon.ENGINE.install(tree,path,weapon.project(payment));
            if(!result.success())return Optional.empty();
            var children=new ArrayList<>(state.installed());children.removeIf(n->n.slotId().equals(slot));
            if(!payment.isEmpty())children.add(new AssemblyState.Installed(slot,AssembledWeapon.identity(payment),payment,true));
            parent.set(AssemblyComponents.STATE.get(),state.updated(children));var changed=AssemblyTrees.replace(held,parentPath,parent);
            AssemblyTrees.validate(changed,AssembledWeapon.identity(changed));
            if(!weapon.project(changed).equals(result.after()))return Optional.empty();
            var refunds=new ArrayList<ItemStack>();old.ifPresent(p->refunds.add(p.stack()));
            if(weapon.feed.affectedBy(path)){
                var gun=IGun.getIGunOrNull(changed);int count=gun.getCurrentAmmoCount(changed);
                if(count>0){var ammo=AmmoBridge.ammunition(changed);if(ammo==null)return Optional.empty();refunds.add(new ItemStack(ammo,count));gun.setCurrentAmmoCount(changed,0);}
                // A chambered round remains usable; no physical loaded-magazine claim is made.
            }
            return Optional.of(new WorkbenchInventoryHost.Change(changed,refunds));
        }catch(IllegalArgumentException invalid){return Optional.empty();}
    }
    private AssemblyGunExchange(){}
}
