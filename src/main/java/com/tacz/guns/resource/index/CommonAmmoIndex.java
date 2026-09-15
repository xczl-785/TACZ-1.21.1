package com.tacz.guns.resource.index;

import com.google.common.base.Preconditions;
import com.tacz.guns.resource.pojo.AmmoIndexPOJO;

/** Caliber/display index. Native item stack-size and sorting metadata are retired. */
public class CommonAmmoIndex {
    private AmmoIndexPOJO pojo;
    private CommonAmmoIndex() {}

    public static CommonAmmoIndex getInstance(AmmoIndexPOJO pojo) {
        Preconditions.checkArgument(pojo != null, "index object file is empty");
        CommonAmmoIndex index = new CommonAmmoIndex();
        index.pojo = pojo;
        return index;
    }

    public AmmoIndexPOJO getPojo() { return pojo; }
}
