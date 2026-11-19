package qouteall.imm_ptl.core;

import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

/** DEVELOPMENT ONLY: runs the mixin audit before the game bootstraps (see DebugMixinAudit). */
public class DebugMixinAuditPreLaunch implements PreLaunchEntrypoint {
    @Override
    public void onPreLaunch() {
        DebugMixinAudit.runIfEnabled();
    }
}
