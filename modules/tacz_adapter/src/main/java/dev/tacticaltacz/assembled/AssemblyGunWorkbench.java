package dev.tacticaltacz.assembled;
import dev.firearms.workbench.*;
import java.util.*;

/** TaCZ's server half of the public workbench: registers the provider that owns its assembled guns. */
public final class AssemblyGunWorkbench {
    private static final AssemblyGunProvider PROVIDER=new AssemblyGunProvider();
    /** Claims the public assembly entry for TaCZ's assembled guns. Called once at startup. */
    public static void register(){WorkbenchProviders.register(PROVIDER);}
    public static WorkbenchProvider provider(){return PROVIDER;}
    private AssemblyGunWorkbench(){}
}
